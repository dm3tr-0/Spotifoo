package com.example.spotifoo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoResult


class MainActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var runtime: GeckoRuntime


    private fun injectAdblockScript() {
        val script = """
        (function() {
            console.log("Adblockify: Starting for GeckoView...");
            
            // Функция перехвата Webpack
            const loadWebpack = () => {
                try {
                    const o = window.webpackChunkclient_web.push([[Symbol()], {}, (n) => n]);
                    const s = Object.keys(o.m).map((n) => o(n));
                    const i = s.filter(n => typeof n == "object").flatMap(n => {
                        try { return Object.values(n); } catch {}
                    });
                    const r = new Set(Object.values(o.m));
                    const c = i.flatMap(n =>
                        typeof n == "function" ? [n] :
                        typeof n == "object" && n ? Object.values(n).filter(p => typeof p == "function" && !r.has(p)) : []
                    );
                    return { cache: s, functionModules: c };
                } catch(e) {
                    console.error("Adblockify: Webpack load failed", e);
                    return { cache: [], functionModules: [] };
                }
            };
            
            // Ждём загрузки Spotify
            let attempts = 0;
            const waitForSpotify = setInterval(() => {
                attempts++;
                console.log("Adblockify: Attempt " + attempts + " - checking Spotify...");
                
                if (document.querySelector('.main-view-container') || window.Spotify) {
                    clearInterval(waitForSpotify);
                    console.log("Adblockify: Spotify loaded, injecting...");
                    
                    try {
                        const modules = loadWebpack();
                        console.log("Adblockify: Found " + modules.functionModules.length + " modules");
                        
                        // Отключаем рекламные сервисы
                        modules.functionModules.forEach(function(module) {
                            if (module && typeof module === 'object') {
                                if (module.disable && typeof module.disable === 'function') {
                                    try {
                                        module.disable();
                                        console.log("Adblockify: Disabled ad module");
                                    } catch(e) {}
                                }
                                if (module.isNewAdsNpvEnabled !== undefined) {
                                    module.isNewAdsNpvEnabled = 0;
                                }
                            }
                        });
                        
                        // Прячем рекламные элементы
                        const style = document.createElement('style');
                        style.id = 'adblockify-css';
                        style.innerHTML = `
                            [data-testid="download-app-button"],
                            .download-banner,
                            .encourage-banner,
                            .ETeoF,
                            .main-leaderboardComponent-container,
                            .sponsor-container,
                            .main-topBar-UpgradeButton,
                            [href*="premium"],
                            button[title*="Upgrade"],
                            div[data-testid*="hpto"] {
                                display: none !important;
                            }
                        `;
                        document.head.appendChild(style);
                        console.log("Adblockify: CSS injected");
                        
                    } catch(e) {
                        console.error("Adblockify: Injection failed", e);
                    }
                } else if (attempts > 30) {
                    clearInterval(waitForSpotify);
                    console.log("Adblockify: Timeout");
                }
            }, 1000);
        })();
    """.trimIndent()


        val encodedScript = android.net.Uri.encode(script)
        session.loadUri("javascript:$encodedScript")
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupGeckoView()
        loadSpotify()
    }


    private fun setupGeckoView() {
        geckoView = findViewById(R.id.gecko_view)
        session = GeckoSession()
        setupPermissions()

        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .build()

        runtime = GeckoRuntime.create(this, runtimeSettings)
        session.open(runtime)


        //session.settings.userAgentOverride =
        //    "Mozilla/5.0 (Linux; Android 14; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.99 Mobile Safari/537.36"

        session.settings.userAgentOverride =
            "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0"

        // Устанавливаем расширение uBlock Origin
        installUBlockExtension()

        // Привязываем сессию к View
        geckoView.setSession(session)
        geckoView.postDelayed({
            injectAdblockScript()
        }, 3000)
    }

    private fun installUBlockExtension() {
        val extensionUri = "resource://android/assets/ublock/"
        val extensionId = "uBlock0@raymondhill.net"

        // ensureBuiltIn возвращает GeckoResult<WebExtension?>
        runtime.webExtensionController.ensureBuiltIn(extensionUri, extensionId)
            .accept(
                { extension ->
                    if (extension != null) {
                        android.util.Log.i("SpotifyPlayer", "✅ uBlock Origin установлен! ID: ${extension.id}")
                    } else {
                        android.util.Log.w("SpotifyPlayer", "⚠️ uBlock Origin вернул null")
                    }
                },
                { error ->
                    android.util.Log.e("SpotifyPlayer", "❌ Ошибка установки uBlock", error)
                }
            )
    }
    //Внутренний класс, который автоматически разрешает DRM
    inner class MyPermissionDelegate : GeckoSession.PermissionDelegate {
        override fun onContentPermissionRequest(
            session: GeckoSession,
            perm: GeckoSession.PermissionDelegate.ContentPermission
        ): GeckoResult<Int>? {
            // Если Spotify запрашивает доступ к DRM - сразу разрешаем
            if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS) {
                return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
            }
            // Все остальные разрешения (если понадобятся) обрабатываем стандартно
            return super.onContentPermissionRequest(session, perm)
        }
    }
    private fun setupPermissions() {
        session.permissionDelegate = MyPermissionDelegate()
    }
    private fun loadSpotify() {
        session.loadUri("https://open.spotify.com")
    }

    override fun onDestroy() {
        super.onDestroy()
        session.close()
    }
}