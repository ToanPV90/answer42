package com.samjdtechnologies.answer42.ui.layout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.service.AuthenticationService;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.theme.lumo.Lumo;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Main layout configuration class using Vaadin AppLayout component.
 * Provides the main navigation structure for the application.
 */
public class MainLayout extends AppLayout implements BeforeEnterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(MainLayout.class);
    
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
        title.addClassName(UIConstants.CSS_LOGO_LAYOUT_TITLE);
        
        // Header layout
        HorizontalLayout logoLayout = new HorizontalLayout(logo, title);
        logoLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        logoLayout.addClassName(UIConstants.CSS_LOGO_LAYOUT);
        
        return logoLayout;
    }
    
    /**
     * Creates the right side components for the navbar
     */
    private HorizontalLayout createRightSideComponents() {
        // User related components
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        LoggingUtil.debug(LOG, "createRightSideComponents", 
            "Creating right side components for authenticated user: %s (authenticated: %s)", 
            username, auth.isAuthenticated());
        
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
        
        // User avatar with dropdown menu
        Avatar avatar = new Avatar(username);
        avatar.addClassName("user-avatar");
        
        // Create dropdown menu for avatar click
        ContextMenu userMenu = new ContextMenu();
        userMenu.setOpenOnClick(true);
        userMenu.setTarget(avatar);
        
        // Create a horizontal layout for each menu item with icon and text
        HorizontalLayout profileLayout = new HorizontalLayout(
            VaadinIcon.USER.create(), new Span("Profile"));
        profileLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        profileLayout.setSpacing(true);
        userMenu.addItem(profileLayout, e -> 
            UI.getCurrent().getPage().executeJs("alert('Profile view not implemented yet')"));
        
        HorizontalLayout subscriptionLayout = new HorizontalLayout(
            VaadinIcon.CREDIT_CARD.create(), new Span("Subscription"));
        subscriptionLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        subscriptionLayout.setSpacing(true);
        userMenu.addItem(subscriptionLayout, e -> 
            UI.getCurrent().getPage().executeJs("alert('Subscription view not implemented yet')"));
        
        HorizontalLayout creditsLayout = new HorizontalLayout(
            VaadinIcon.COIN_PILES.create(), new Span("Credits"));
        creditsLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        creditsLayout.setSpacing(true);
        userMenu.addItem(creditsLayout, e -> 
            UI.getCurrent().getPage().executeJs("alert('Credits view not implemented yet')"));
        
        HorizontalLayout settingsLayout = new HorizontalLayout(
            VaadinIcon.COG.create(), new Span("Settings"));
        settingsLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        settingsLayout.setSpacing(true);
        userMenu.addItem(settingsLayout, e -> 
            UI.getCurrent().getPage().executeJs("alert('Settings view not implemented yet')"));
        
        // Add a separator
        userMenu.add(new Hr());
        
        // Add logout option
        HorizontalLayout logoutLayout = new HorizontalLayout(
            VaadinIcon.SIGN_OUT.create(), new Span("Logout"));
        logoutLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        logoutLayout.setSpacing(true);
        userMenu.addItem(logoutLayout, e -> logout());
        
                
        // Right side items
        HorizontalLayout rightItems = new HorizontalLayout(search, themeToggle, avatar);
        rightItems.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        rightItems.setSpacing(true);
        rightItems.addClassName(UIConstants.CSS_TOOLBAR_RIGHT_ITEMS);
        
        return rightItems;
    }
    
    /**
     * Creates the drawer content with navigation, logout button and footer
     */
    private Scroller createDrawerContent(SideNav nav) {
        // Add assistant footer to drawer
        Footer footer = createAssistantFooter();
        
        // Create a vertical layout for drawer content
        VerticalLayout drawerContent = new VerticalLayout();
        drawerContent.add(nav, footer);
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
        // Create vertical layout to hold all sidebar components
        VerticalLayout sidebarLayout = new VerticalLayout();
        sidebarLayout.setPadding(false);
        sidebarLayout.setSpacing(false);
        
        // Create MAIN section
        Span mainHeader = new Span("MAIN");
        mainHeader.addClassName(UIConstants.CSS_NAV_SECTION_HEADER);
        sidebarLayout.add(mainHeader);
        
        // Create main nav items
        SideNav mainNav = new SideNav();
        mainNav.addClassNames(UIConstants.CSS_MAIN_NAV);
        
        SideNavItem dashboardItem = new SideNavItem("Dashboard", UIConstants.ROUTE_MAIN, VaadinIcon.DASHBOARD.create());
        dashboardItem.addClassNames(UIConstants.CSS_NAV_ITEM, UIConstants.CSS_SIDEBAR_NAV_ITEM);
        
        SideNavItem papersItem = new SideNavItem("Papers", UIConstants.ROUTE_PAPERS, VaadinIcon.FILE_TEXT.create());
        papersItem.addClassNames(UIConstants.CSS_NAV_ITEM, UIConstants.CSS_SIDEBAR_NAV_ITEM);
        
        SideNavItem projectsItem = new SideNavItem("Projects", UIConstants.ROUTE_PROJECTS, VaadinIcon.FOLDER.create());
        projectsItem.addClassNames(UIConstants.CSS_NAV_ITEM, UIConstants.CSS_SIDEBAR_NAV_ITEM);
        
        SideNavItem aiChatItem = new SideNavItem("AI Chat", UIConstants.ROUTE_AI_CHAT, VaadinIcon.COMMENTS.create());
        aiChatItem.addClassNames(UIConstants.CSS_NAV_ITEM, UIConstants.CSS_SIDEBAR_NAV_ITEM);
        
        mainNav.addItem(dashboardItem);
        mainNav.addItem(papersItem);
        mainNav.addItem(projectsItem);
        mainNav.addItem(aiChatItem);
        
        sidebarLayout.add(mainNav);
        
        // Add divider
        Div divider = new Div();
        divider.addClassName(UIConstants.CSS_NAV_DIVIDER);
        sidebarLayout.add(divider);
        
        // Create USER section
        Span userHeader = new Span("USER");
        userHeader.addClassName(UIConstants.CSS_NAV_SECTION_HEADER);
        sidebarLayout.add(userHeader);
        
        // Create user nav items
        SideNav userNav = new SideNav();
        userNav.addClassNames(UIConstants.CSS_USER_NAV);
        
        SideNavItem profileItem = new SideNavItem("Profile", UIConstants.ROUTE_PROFILE, VaadinIcon.USER.create());
        profileItem.addClassNames(UIConstants.CSS_NAV_ITEM, UIConstants.CSS_SIDEBAR_NAV_ITEM);
        
        SideNavItem subscriptionItem = new SideNavItem("Subscription", UIConstants.ROUTE_PROFILE, VaadinIcon.CREDIT_CARD.create());
        subscriptionItem.addClassNames(UIConstants.CSS_NAV_ITEM, UIConstants.CSS_SIDEBAR_NAV_ITEM);
        
        SideNavItem creditsItem = new SideNavItem("Credits", UIConstants.ROUTE_PROFILE, VaadinIcon.COIN_PILES.create());
        creditsItem.addClassNames(UIConstants.CSS_NAV_ITEM, UIConstants.CSS_SIDEBAR_NAV_ITEM);
        
        SideNavItem settingsItem = new SideNavItem("Settings", UIConstants.ROUTE_SETTINGS, VaadinIcon.COG.create());
        settingsItem.addClassNames(UIConstants.CSS_NAV_ITEM, UIConstants.CSS_SIDEBAR_NAV_ITEM);
        
        // Create a special SideNavItem for logout with a custom click listener
        SideNavItem logoutItem = new SideNavItem("Logout", "javascript:void(0)", VaadinIcon.SIGN_OUT.create());
        logoutItem.addClassNames(UIConstants.CSS_NAV_ITEM, UIConstants.CSS_SIDEBAR_NAV_ITEM, UIConstants.CSS_LOGOUT_ITEM);
        logoutItem.getElement().addEventListener("click", e -> logout());
        
        userNav.addItem(profileItem);
        userNav.addItem(subscriptionItem);
        userNav.addItem(creditsItem);
        userNav.addItem(settingsItem);
        userNav.addItem(logoutItem);
        
        sidebarLayout.add(userNav);
        
        // Add VerticalLayout to a container that the drawer can use
        VerticalLayout container = new VerticalLayout(sidebarLayout);
        container.setPadding(false);
        container.setSpacing(false);
        
        // Create a single SideNav to return
        SideNav returnNav = new SideNav();
        returnNav.getElement().appendChild(container.getElement());
        
        return returnNav;
    }
    
    /**
     * Creates the assistant footer for the drawer
     */
    private Footer createAssistantFooter() {
        Footer footer = new Footer();
        footer.addClassNames(UIConstants.CSS_DRAWER_FOOTER, UIConstants.CSS_SIDEBAR_FOOTER);
        
        Avatar assistantAvatar = new Avatar("A");
        assistantAvatar.addClassNames(UIConstants.CSS_ASSISTANT_AVATAR, UIConstants.CSS_USER_INITIAL);
        
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
     * 
     * Note: VaadinSession invalidation is handled by AuthenticationService.logout(),
     * so we don't need to invalidate it again here.
     */
    private void logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LoggingUtil.debug(LOG, "logout", "Logging out user: %s", 
                auth != null ? auth.getName() : "unknown");
        
        try {
            // Log the user out (includes session invalidation)
            authenticationService.logout();
            
            // Redirect to login page
            UI.getCurrent().navigate(UIConstants.ROUTE_LOGIN);
            LoggingUtil.info(LOG, "logout", "User logged out and redirected to login page");
        } catch (Exception e) {
            LoggingUtil.error(LOG, "logout", "Error during logout", e);
            // Still try to redirect to login page in case of errors
            UI.getCurrent().navigate(UIConstants.ROUTE_LOGIN);
        }
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        this.content.getElement().appendChild(content.getElement());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Skip authentication check for login and register pages
        String path = event.getLocation().getPath();
        if (path.equals(UIConstants.ROUTE_LOGIN) || path.equals(UIConstants.ROUTE_REGISTER)) {
            return;
        }
        
        // Check if the user is anonymous or lacks required role
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // Check for proper authentication - anonymous users shouldn't access the main layout
        boolean isAuthenticated = false;
        
        if (auth != null && auth.getAuthorities() != null) {
            // Check if user has the required ROLE_USER authority
            isAuthenticated = auth.getAuthorities().stream()
                    .anyMatch(grantedAuth -> "ROLE_USER".equals(grantedAuth.getAuthority()));
        }
        
        if (!isAuthenticated) {
            LoggingUtil.debug(LOG, "MainLayout", "User not properly authenticated, redirecting to login page");
            event.forwardTo(UIConstants.ROUTE_LOGIN);
        }
    }
}
