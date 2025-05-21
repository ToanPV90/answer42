package com.samjdtechnologies.answer42.ui;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Meta;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;

/**
 * Application shell configuration.
 * This class is used to configure the application shell, which is the top-level
 * container for all Vaadin UI components.
 */
@PWA(
    name = "Answer42 Application",
    shortName = "Answer42",
    description = "Answer42 - Spring Boot and Vaadin Application",
    iconPath = "favicon.svg",
    backgroundColor = "#6366f1",
    themeColor = "#4f46e5",
    offlinePath = "offline.html",
    offlineResources = {"images/answer42-logo.svg", "favicon.ico", "favicon.svg"}
)
@CssImport("./styles/themes/answer42/main.css")
@CssImport("./styles/themes/answer42/styles.css")
@CssImport("./styles/themes/answer42/components/auth-forms.css")
@CssImport("./styles/themes/answer42/components/main-layout.css")
@CssImport("./styles/themes/answer42/components/dashboard.css")
@CssImport("./styles/themes/answer42/components/papers.css")
@CssImport("./styles/themes/answer42/components/search-icon.css")
@CssImport("./styles/themes/answer42/components/profile.css")
@CssImport("./styles/themes/answer42/components/subscription.css")
@CssImport("./styles/themes/answer42/components/credits.css")
@CssImport("./styles/themes/answer42/components/settings.css")
@CssImport("./styles/themes/answer42/components/upload-paper.css")
@CssImport("./styles/themes/answer42/components/bulk-upload.css")
@CssImport("./styles/themes/answer42/components/projects.css")
@CssImport("./styles/themes/answer42/components/ai-chat.css")

@JavaScript("./sw-loader.js")
@JavaScript("./jwt-injector.js")
@JavaScript("./sw-register.js")

@Meta(name = "author", content = "SAMJD Technologies")
@Meta(name = "application-name", content = "Answer42")
@Meta(name = "apple-mobile-web-app-title", content = "Answer42")
@Meta(name = "msapplication-TileColor", content="#6366f1")
@Viewport("width=device-width, initial-scale=1.0")
public class AppShell implements AppShellConfigurator {
    
    /**
     * Configure the application shell.
     * This method is called by Vaadin when the application shell is being configured.
     */
    @Override
    public void configurePage(AppShellSettings settings) {
        // Add favicon links for various browsers and platforms
        settings.addFavIcon("icon", "favicon.ico", "16x16");
        settings.addFavIcon("icon", "favicon.svg", "32x32");
        settings.addFavIcon("icon", "favicon.svg", "96x96");
        
        // Apple touch icons
        settings.addLink("apple-touch-icon", "favicon.svg");
        
        // Microsoft specific
        settings.addLink("msapplication-TileImage", "favicon.svg");
        
        // Link to the manifest
        settings.addLink("manifest", "manifest.webmanifest");

        // settings.addLink("./sw-register.js", Map.of("rel", "preload", "as", "script"));

    }
}
