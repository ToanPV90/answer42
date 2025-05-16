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
public class MainLayout extends AppLayout {
    
    private final AuthenticationService authenticationService;
    private Div content;
    private static final String CONTENT_PADDING = "var(--lumo-space-l)";
    
    public MainLayout(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        
        addClassName(UIConstants.CSS_MAIN_VIEW);
        
        // Create content container with proper padding
        content = new Div();
        content.setSizeFull();
        content.addClassNames("main-content", "content");
        content.getStyle().set("padding", CONTENT_PADDING);
        setContent(content);
        
        // Core AppLayout components following the AppLayoutNavbarPlacement pattern
        DrawerToggle toggle = new DrawerToggle();
        
        // App header/title section
        HorizontalLayout logoLayout = createLogoLayout();
        
        // User related components in navbar
        HorizontalLayout rightItems = createRightSideComponents();
        
        // Navigation components for drawer
        SideNav nav = createSideNav();
        
        // Create the drawer content with navigation, logout button and footer
        Scroller scroller = createDrawerContent(nav);
        
        // Add components to layout
        addToDrawer(scroller);
        addToNavbar(toggle, logoLayout, rightItems);
    }
    
    /**
     * Creates the logo and title layout for the navbar
     */
    private HorizontalLayout createLogoLayout() {
        // App title with logo
        Image logo = new Image("frontend/images/answer42-logo.svg", "Answer42 Logo");
        logo.setHeight("32px");
        logo.setWidth("32px");
        
        H1 title = new H1("Answer42");
        title.getStyle()
            .set("font-size", "var(--lumo-font-size-l)")
            .set("margin", "0");
        
        // Header layout
        HorizontalLayout logoLayout = new HorizontalLayout(logo, title);
        logoLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        logoLayout.addClassName("logo-layout");
        
        return logoLayout;
    }
    
    /**
     * Creates the right side components for the navbar
     */
    private HorizontalLayout createRightSideComponents() {
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
        
        // Right align the components using CSS margin-left: auto
        rightItems.getStyle()
            .set("margin-left", "auto")
            .set("padding", "10px");
        
        return rightItems;
    }
    
    /**
     * Creates the drawer content with navigation, logout button and footer
     */
    private Scroller createDrawerContent(SideNav nav) {
        // Create logout button with proper styling
        Button logoutButton = new Button("Logout", e -> logout());
        logoutButton.setIcon(VaadinIcon.SIGN_OUT.create());
        logoutButton.addClassName("sidebar-nav-item");
        logoutButton.getStyle()
            .set("margin", "var(--lumo-space-m)")
            .set("width", "calc(100% - var(--lumo-space-m) * 2)")
            .set("color", "var(--lumo-error-color)");
        
        // Add assistant footer to drawer
        Footer footer = createAssistantFooter();
        
        // Create a vertical layout for drawer content
        VerticalLayout drawerContent = new VerticalLayout();
        drawerContent.add(nav, logoutButton, footer);
        drawerContent.setSizeFull();
        drawerContent.expand(nav);
        drawerContent.setSpacing(false);
        drawerContent.setPadding(false);
        
        // Wrap in scroller following AppLayoutNavbarPlacement pattern
        Scroller scroller = new Scroller(drawerContent);
        scroller.setClassName(LumoUtility.Padding.SMALL);
        
        return scroller;
    }
    
    /**
     * Creates the navigation menu
     */
    private SideNav createSideNav() {
        SideNav sideNav = new SideNav();
        sideNav.addClassNames("main-nav", LumoUtility.Padding.SMALL);
        
        // Main section - using proper routes from UIConstants with consistent styling
        SideNavItem dashboardItem = new SideNavItem("Dashboard", UIConstants.ROUTE_MAIN, VaadinIcon.DASHBOARD.create());
        dashboardItem.addClassNames("nav-item", "sidebar-nav-item");
        
        SideNavItem papersItem = new SideNavItem("Papers", UIConstants.ROUTE_PAPERS, VaadinIcon.FILE_TEXT.create());
        papersItem.addClassNames("nav-item", "sidebar-nav-item");
        
        SideNavItem projectsItem = new SideNavItem("Projects", UIConstants.ROUTE_PROJECTS, VaadinIcon.FOLDER.create());
        projectsItem.addClassNames("nav-item", "sidebar-nav-item");
        
        SideNavItem aiChatItem = new SideNavItem("AI Chat", UIConstants.ROUTE_AI_CHAT, VaadinIcon.COMMENTS.create());
        aiChatItem.addClassNames("nav-item", "sidebar-nav-item");
        
        sideNav.addItem(dashboardItem);
        sideNav.addItem(papersItem);
        sideNav.addItem(projectsItem);
        sideNav.addItem(aiChatItem);
        
        // User section - expanded by default
        SideNavItem userNav = new SideNavItem("User");
        userNav.setPrefixComponent(VaadinIcon.USER.create());
        userNav.setExpanded(true); // Open by default
        userNav.addClassNames("sidebar-nav-item");
        
        // User submenu items with consistent styling - shown below parent item
        SideNavItem profileItem = new SideNavItem("Profile", UIConstants.ROUTE_PROFILE, VaadinIcon.USER.create());
        profileItem.addClassNames("sidebar-nav-item");
        userNav.addItem(profileItem);
        
        SideNavItem subscriptionItem = new SideNavItem("Subscription", UIConstants.ROUTE_PROFILE, VaadinIcon.CREDIT_CARD.create());
        subscriptionItem.addClassNames("sidebar-nav-item");
        userNav.addItem(subscriptionItem);
        
        SideNavItem creditsItem = new SideNavItem("Credits", UIConstants.ROUTE_PROFILE, VaadinIcon.COIN_PILES.create());
        creditsItem.addClassNames("sidebar-nav-item");
        userNav.addItem(creditsItem);
        
        SideNavItem settingsItem = new SideNavItem("Settings", UIConstants.ROUTE_SETTINGS, VaadinIcon.COG.create());
        settingsItem.addClassNames("sidebar-nav-item");
        userNav.addItem(settingsItem);
        
        // Add user section to nav
        sideNav.addItem(userNav);
        
        return sideNav;
    }
    
    /**
     * Creates the assistant footer for the drawer
     */
    private Footer createAssistantFooter() {
        Footer footer = new Footer();
        footer.addClassNames("drawer-footer", "sidebar-footer");
        
        Avatar assistantAvatar = new Avatar("A");
        assistantAvatar.addClassNames("assistant-avatar", "user-initial");
        assistantAvatar.getStyle()
            .set("background-color", "var(--lumo-primary-color)")
            .set("color", "white")
            .set("font-weight", "600");
        
        VerticalLayout assistantInfo = new VerticalLayout();
        assistantInfo.setPadding(false);
        assistantInfo.setSpacing(false);
        assistantInfo.addClassName("assistant-text");
        
        H3 assistantName = new H3("Answer42");
        assistantName.addClassName("assistant-name");
        
        Span assistantRole = new Span("Research Assistant");
        assistantRole.addClassName("assistant-role");
        
        assistantInfo.add(assistantName, assistantRole);
        
        HorizontalLayout footerLayout = new HorizontalLayout(assistantAvatar, assistantInfo);
        footerLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        footerLayout.addClassName("assistant-container");
        footerLayout.setSpacing(true);
        footer.add(footerLayout);
        
        return footer;
    }
    
    /**
     * Handles user logout
     */
    private void logout() {
        // Log the user out
        authenticationService.logout();

        // Clear session
        VaadinSession.getCurrent().getSession().invalidate();

        // Redirect to login page
        UI.getCurrent().navigate(UIConstants.ROUTE_LOGIN);
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        this.content.getElement().appendChild(content.getElement());
    }
}
