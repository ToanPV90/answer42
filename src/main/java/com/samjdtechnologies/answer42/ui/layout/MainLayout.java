package com.samjdtechnologies.answer42.ui.layout;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.service.AuthenticationService;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.ThemeList;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.lumo.Lumo;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Main layout configuration class using Vaadin AppLayout component.
 * Provides the main navigation structure for the application.
 */
public class MainLayout extends AppLayout{
    
    private final AuthenticationService authenticationService;
    private Div content;
    
    public MainLayout(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        
        addClassName(UIConstants.CSS_MAIN_VIEW);
        
        // Create content container
        content = new Div();
        content.setSizeFull();
        content.addClassName("main-content");
        setContent(content);
        
        // Setup layout
        DrawerToggle toggle = new DrawerToggle();
        
        // App title
        Image logo = new Image("frontend/images/answer42-logo.svg", "Answer42 Logo");
        logo.setHeight("32px");
        logo.setWidth("32px");
        
        H1 title = new H1("Answer42");
        title.getStyle()
            .set("font-size", "var(--lumo-font-size-l)")
            .set("margin", "0");
        
        // Header components
        HorizontalLayout logoLayout = new HorizontalLayout(logo, title);
        logoLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        logoLayout.addClassName("logo-layout");
        
        // User related components
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        // Search field
        TextField search = new TextField();
        search.setPlaceholder("Search papers, projects...");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.addClassName("search-field");
        search.setWidth("300px");
        
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
        
        // User avatar
        Avatar avatar = new Avatar(username);
        avatar.addClassName("user-avatar");
        
        // Right side items
        HorizontalLayout rightItems = new HorizontalLayout(search, themeToggle, avatar);
        rightItems.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        rightItems.setSpacing(true);
        
        // Create navigation
        SideNav nav = createSideNav();
        
        // Create logout button
        Button logoutButton = new Button("Logout", e -> logout());
        logoutButton.setIcon(VaadinIcon.SIGN_OUT.create());
        logoutButton.getStyle()
            .set("margin", "var(--lumo-space-m)")
            .set("width", "calc(100% - var(--lumo-space-m) * 2)");
        
        // Add assistant footer to drawer
        Footer footer = createAssistantFooter();
        
        // Create a vertical layout for drawer content that includes the logout button
        VerticalLayout drawerContent = new VerticalLayout();
        drawerContent.add(nav, logoutButton, footer);
        drawerContent.setSizeFull();
        drawerContent.expand(nav);
        drawerContent.setSpacing(false);
        drawerContent.setPadding(false);
        
        // Wrap in scroller
        Scroller scroller = new Scroller(drawerContent);
        scroller.setClassName(LumoUtility.Padding.SMALL);
        
        // Add components to layout
        addToDrawer(scroller);
        addToNavbar(toggle, logoLayout, rightItems);
    }
    
    @Override
    public void showRouterLayoutContent(HasElement content) {
        this.content.getElement().appendChild(content.getElement());
    }
    
    private SideNav createSideNav() {
        SideNav sideNav = new SideNav();
        
        // Main section - using proper routes from UIConstants
        sideNav.addItem(new SideNavItem("Dashboard", UIConstants.ROUTE_DASHBOARD, VaadinIcon.DASHBOARD.create()));
        sideNav.addItem(new SideNavItem("Papers", UIConstants.ROUTE_PAPERS, VaadinIcon.FILE_TEXT.create()));
        sideNav.addItem(new SideNavItem("Projects", UIConstants.ROUTE_PROJECTS, VaadinIcon.FOLDER.create()));
        sideNav.addItem(new SideNavItem("AI Chat", UIConstants.ROUTE_AI_CHAT, VaadinIcon.COMMENTS.create()));
        
        // User section
        SideNavItem userNav = new SideNavItem("User");
        userNav.setPrefixComponent(VaadinIcon.USER.create());
        
        // User submenu items
        userNav.addItem(new SideNavItem("Profile", UIConstants.ROUTE_PROFILE, VaadinIcon.USER.create()));
        userNav.addItem(new SideNavItem("Subscription", UIConstants.ROUTE_PROFILE, VaadinIcon.CREDIT_CARD.create()));
        userNav.addItem(new SideNavItem("Credits", UIConstants.ROUTE_PROFILE, VaadinIcon.COIN_PILES.create()));
        userNav.addItem(new SideNavItem("Settings", UIConstants.ROUTE_SETTINGS, VaadinIcon.COG.create()));
        
        // Add user section to nav
        sideNav.addItem(userNav);
        
        return sideNav;
    }
    
    
    private Footer createAssistantFooter() {
        Footer footer = new Footer();
        footer.addClassName("drawer-footer");
        
        Avatar assistantAvatar = new Avatar("N");
        assistantAvatar.addClassName("assistant-avatar");
        
        VerticalLayout assistantInfo = new VerticalLayout();
        assistantInfo.setPadding(false);
        assistantInfo.setSpacing(false);
        
        H3 assistantName = new H3("Answer42");
        assistantName.addClassName("assistant-name");
        
        Span assistantRole = new Span("Research Assistant");
        assistantRole.addClassName("assistant-role");
        
        assistantInfo.add(assistantName, assistantRole);
        
        HorizontalLayout footerLayout = new HorizontalLayout(assistantAvatar, assistantInfo);
        footerLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        footer.add(footerLayout);
        
        return footer;
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
