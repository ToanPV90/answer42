package com.samjdtechnologies.answer42.ui.views;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.service.AuthenticationService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.ThemeList;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.lumo.Lumo;

import jakarta.annotation.security.PermitAll;

/**
 * Main view of the application with navigation sidebar.
 * This serves as the main layout for the application after authentication.
 */
@Route(UIConstants.ROUTE_MAIN)
@PageTitle("Answer42")
@PermitAll
public class MainView extends Div implements RouterLayout {

    private final AuthenticationService authenticationService;
    private final Div contentWrapper;
    private final Div content;

    public MainView(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;

        // Configure main layout
        setSizeFull();
        addClassName(UIConstants.CSS_MAIN_VIEW);

        // Create sidebar
        Component sidebar = createSidebar();
        
        // Create header/navbar
        Component header = createHeader();

        // Create content area
        contentWrapper = new Div();
        contentWrapper.addClassName(UIConstants.CSS_CONTENT_WRAPPER);
        
        content = new Div();
        content.addClassName(UIConstants.CSS_CONTENT);
        content.setSizeFull();

        contentWrapper.add(header, content);

        // Add components to main layout
        add(sidebar, contentWrapper);
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        this.content.getElement().appendChild(content.getElement());
    }
    
    private Component createHeader() {
        Div header = new Div();
        header.addClassName("main-header");
        
        // Search field
        TextField search = new TextField();
        search.setPlaceholder("Search papers, projects...");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.addClassName("search-field");
        
        // Right side components
        Div rightSection = new Div();
        rightSection.addClassName("header-right");
        
        // Theme toggle button
        Button themeToggle = new Button(new Icon(VaadinIcon.MOON));
        themeToggle.addClassName("theme-toggle");
        themeToggle.addClickListener(e -> {
            ThemeList themeList = UI.getCurrent().getElement().getThemeList();
            if (themeList.contains(Lumo.DARK)) {
                themeList.remove(Lumo.DARK);
                themeToggle.setIcon(new Icon(VaadinIcon.MOON));
            } else {
                themeList.add(Lumo.DARK);
                themeToggle.setIcon(new Icon(VaadinIcon.SUN_O));
            }
        });
        
        // User menu
        Div userMenu = new Div();
        userMenu.addClassName("user-menu");
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        Span userInitial = new Span(username.substring(0, 1).toUpperCase());
        userInitial.addClassName("user-initial");
        
        Span userName = new Span(username);
        userName.addClassName("user-name");
        
        Icon dropdownIcon = VaadinIcon.CHEVRON_DOWN.create();
        dropdownIcon.addClassName("dropdown-icon");
        
        userMenu.add(userInitial, userName, dropdownIcon);
        
        rightSection.add(themeToggle, userMenu);
        
        header.add(search, rightSection);
        return header;
    }

    private Component createSidebar() {
        // Create sidebar container
        Div sidebar = new Div();
        sidebar.addClassName(UIConstants.CSS_SIDEBAR);

        // Create sidebar header with logo
        Div headerLayout = new Div();
        headerLayout.addClassName("sidebar-header");

        Div logoLayout = new Div();
        logoLayout.addClassName("sidebar-logo");

        Image logo = new Image("frontend/images/answer42-logo.svg", "Answer42 Logo");
        logo.setHeight("32px");
        logo.setWidth("32px");

        Span logoText = new Span("Answer42");
        logoText.addClassName("sidebar-logo-text");

        logoLayout.add(logo, logoText);
        headerLayout.add(logoLayout);

        // Create main navigation section
        Div mainNavSection = new Div();
        mainNavSection.addClassName("sidebar-section");

        Span mainNavTitle = new Span("MAIN");
        mainNavTitle.addClassName("sidebar-section-title");

        // Navigation links with improved icons
        Component dashboardLink = createNavItem(VaadinIcon.DASHBOARD, "Dashboard", UIConstants.ROUTE_DASHBOARD);
        Component papersLink = createNavItem(VaadinIcon.FILE_TEXT, "Papers", UIConstants.ROUTE_PAPERS);
        Component projectsLink = createNavItem(VaadinIcon.FOLDER, "Projects", UIConstants.ROUTE_PROJECTS);
        Component aiChatLink = createNavItem(VaadinIcon.COMMENTS, "AI Chat", UIConstants.ROUTE_AI_CHAT);

        mainNavSection.add(mainNavTitle, dashboardLink, papersLink, projectsLink, aiChatLink);

        // Create user section
        Div userSection = new Div();
        userSection.addClassName("sidebar-section");

        Span userSectionTitle = new Span("USER");
        userSectionTitle.addClassName("sidebar-section-title");

        Component profileLink = createNavItem(VaadinIcon.USER, "Profile", UIConstants.ROUTE_PROFILE);
        Component subscriptionLink = createNavItem(VaadinIcon.CREDIT_CARD, "Subscription", UIConstants.ROUTE_PROFILE);
        Component creditsLink = createNavItem(VaadinIcon.COIN_PILES, "Credits", UIConstants.ROUTE_PROFILE);
        Component settingsLink = createNavItem(VaadinIcon.COG, "Settings", UIConstants.ROUTE_SETTINGS);
        Component logoutItem = createNavItem(VaadinIcon.SIGN_OUT, "Logout", null, true);

        userSection.add(userSectionTitle, profileLink, subscriptionLink, creditsLink, settingsLink, logoutItem);

        // Create sidebar footer
        Div footer = new Div();
        footer.addClassName("sidebar-footer");

        Div assistantContainer = new Div();
        assistantContainer.addClassName("assistant-container");
        
        Span assistantName = new Span("Answer42");
        assistantName.addClassName("assistant-name");
        
        Span assistantRole = new Span("Research Assistant");
        assistantRole.addClassName("assistant-role");
        
        Span assistantAvatar = new Span("N");
        assistantAvatar.addClassName("assistant-avatar");
        
        assistantContainer.add(assistantAvatar);
        
        Div assistantTextContainer = new Div();
        assistantTextContainer.addClassName("assistant-text");
        assistantTextContainer.add(assistantName, assistantRole);
        
        footer.add(assistantContainer, assistantTextContainer);

        // Add all sections to sidebar
        sidebar.add(headerLayout, mainNavSection, userSection, footer);

        return sidebar;
    }

    private Component createNavItem(VaadinIcon icon, String text, String route) {
        return createNavItem(icon, text, route, false);
    }

    private Component createNavItem(VaadinIcon icon, String text, String route, boolean isLogout) {
        Div navItem = new Div();
        navItem.addClassName("sidebar-nav-item");

        if (route != null && getUrl().equals(route)) {
            navItem.addClassName("active");
        }

        Div iconDiv = new Div();
        iconDiv.addClassName("sidebar-nav-item-icon");
        
        Icon navIcon = icon.create();
        navIcon.setSize("20px");
        iconDiv.add(navIcon);

        Span label = new Span(text);

        navItem.add(iconDiv, label);

        if (isLogout) {
            navItem.addClickListener(e -> logout());
        } else if (route != null) {
            navItem.addClickListener(e -> UI.getCurrent().navigate(route));
        }

        return navItem;
    }

    private String getUrl() {
        String path = UI.getCurrent().getInternals().getActiveViewLocation().getPath();
        return path.isEmpty() ? UIConstants.ROUTE_MAIN : path;
    }

    private void logout() {
        // Log the user out
        authenticationService.logout();

        // Clear session
        VaadinSession.getCurrent().getSession().invalidate();

        // Redirect to login page
        UI.getCurrent().navigate(UIConstants.ROUTE_LOGIN);
    }
}
