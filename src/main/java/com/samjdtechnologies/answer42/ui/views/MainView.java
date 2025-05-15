package com.samjdtechnologies.answer42.ui.views;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.service.AuthenticationService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.dom.ThemeList;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.lumo.Lumo;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
        
        // Create content area
        contentWrapper = new Div();
        contentWrapper.addClassName(UIConstants.CSS_CONTENT_WRAPPER);
        
        content = new Div();
        content.addClassName(UIConstants.CSS_CONTENT);
        content.setSizeFull();
        
        contentWrapper.add(content);
        
        // Add components to main layout
        add(sidebar, contentWrapper);
    }
    
    @Override
    public void showRouterLayoutContent(HasElement content) {
        this.content.getElement().appendChild(content.getElement());
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
        logo.setHeight("36px");
        logo.setWidth("36px");
        
        Span logoText = new Span("Answer42");
        logoText.addClassName("sidebar-logo-text");
        
        logoLayout.add(logo, logoText);
        headerLayout.add(logoLayout);
        
        // Create main navigation section
        Div mainNavSection = new Div();
        mainNavSection.addClassName("sidebar-section");
        
        Span mainNavTitle = new Span("MAIN");
        mainNavTitle.addClassName("sidebar-section-title");
        
        // Navigation links
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
        Component settingsLink = createNavItem(VaadinIcon.COG, "Settings", UIConstants.ROUTE_SETTINGS);
        Component logoutItem = createNavItem(VaadinIcon.SIGN_OUT, "Logout", null, true);
        
        userSection.add(userSectionTitle, profileLink, settingsLink, logoutItem);
        
        // Create sidebar footer
        Div footer = new Div();
        footer.addClassName("sidebar-footer");
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        Span footerText = new Span("Answer42 Research Assistant");
        footer.add(footerText);
        
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
        iconDiv.add(icon.create());
        
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
        UI.getCurrent().getInternals().getActiveViewLocation().getPath();
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
