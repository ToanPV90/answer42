package com.samjdtechnologies.answer42.ui.layout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.service.UserService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.service.AuthenticationService;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
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
    private final UserService userService;
    private User currentUser;

    /**
     * Constructs the main layout for the application with navigation drawer and header.
     * Sets up the application structure including navigation, user controls, and theme switching.
     * 
     * @param authenticationService the authentication service for user login/logout operations
     * @param userService the user service for fetching user information
     */
    public MainLayout(AuthenticationService authenticationService, UserService userService) {
        this.authenticationService = authenticationService;
        this.userService = userService;
        
        addClassName(UIConstants.CSS_MAIN_VIEW);
        
        // Apply padding to content area directly using CSS custom property
        getElement().getStyle().set("--app-layout-content-padding", "var(--lumo-space-l)");
        
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
     * Creates the logo and title layout for the navbar.
     */
    private HorizontalLayout createLogoLayout() {
        // App title with logo
        Image logo = new Image("frontend/images/answer42-logo.svg", "Answer42 Logo");
        logo.setHeight("65px");
        logo.setWidth("65px");
        
        H1 title = new H1("Answer42");
        title.addClassName(UIConstants.CSS_LOGO_LAYOUT_TITLE);
        
        // Header layout
        HorizontalLayout logoLayout = new HorizontalLayout(logo, title);
        logoLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        logoLayout.addClassName(UIConstants.CSS_LOGO_LAYOUT);
        
        return logoLayout;
    }
    
    /**
     * Creates the right side components for the navbar.
     * 
     * @return a HorizontalLayout containing search field, theme toggle button, and user avatar
     */
    private HorizontalLayout createRightSideComponents() {
        // User related components
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        LoggingUtil.debug(LOG, "createRightSideComponents", 
            "Creating right side components for authenticated user: %s (authenticated: %s)", 
            username, auth.isAuthenticated());
        
        // Search field with enhanced colored icon
        TextField search = new TextField();
        search.setPlaceholder("Search papers, projects...");
        
        // Create and style the search icon
        Icon searchIcon = VaadinIcon.SEARCH.create();
        searchIcon.addClassName(UIConstants.CSS_SEARCH_ICON);
        
        // Add the enhanced icon to the search field
        search.setPrefixComponent(searchIcon);
        search.addClassName(UIConstants.CSS_SEARCH_FIELD);
        search.setWidth("300px");
        
        // Theme toggle button
        Button themeToggle = new Button(new Icon(VaadinIcon.MOON));
        themeToggle.addClassName(UIConstants.CSS_THEME_TOGGLE);
        themeToggle.setHeight("65px");
        themeToggle.setWidth("65px");
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
        avatar.addClassName(UIConstants.CSS_USER_AVATAR);
        avatar.setHeight("65px");
        avatar.setWidth("65px");
        
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
            UI.getCurrent().navigate(UIConstants.ROUTE_PROFILE));
        
        HorizontalLayout subscriptionLayout = new HorizontalLayout(
            VaadinIcon.CREDIT_CARD.create(), new Span("Subscription"));
        subscriptionLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        subscriptionLayout.setSpacing(true);
        userMenu.addItem(subscriptionLayout, e -> 
            UI.getCurrent().navigate(UIConstants.ROUTE_SUBSCRIPTION));
        
        HorizontalLayout creditsLayout = new HorizontalLayout(
            VaadinIcon.COIN_PILES.create(), new Span("Credits"));
        creditsLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        creditsLayout.setSpacing(true);
        userMenu.addItem(creditsLayout, e -> 
            UI.getCurrent().navigate(UIConstants.ROUTE_CREDITS));
        
        HorizontalLayout settingsLayout = new HorizontalLayout(
            VaadinIcon.COG.create(), new Span("Settings"));
        settingsLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        settingsLayout.setSpacing(true);
        userMenu.addItem(settingsLayout, e -> 
            UI.getCurrent().navigate(UIConstants.ROUTE_SETTINGS));
        
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
     * Creates the drawer content with navigation, logout button and footer.
     * 
     * @param nav the SideNav navigation component to include in the drawer
     * @return a Scroller component containing the drawer content
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
     * Creates the navigation menu.
     * 
     * @return a SideNav component with all navigation items
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
        
        SideNavItem subscriptionItem = new SideNavItem("Subscription", UIConstants.ROUTE_SUBSCRIPTION, VaadinIcon.CREDIT_CARD.create());
        subscriptionItem.addClassNames(UIConstants.CSS_NAV_ITEM, UIConstants.CSS_SIDEBAR_NAV_ITEM);
        
        SideNavItem creditsItem = new SideNavItem("Credits", UIConstants.ROUTE_CREDITS, VaadinIcon.COIN_PILES.create());
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
     * Creates the assistant footer for the drawer.
     * 
     * @return a Footer component containing the assistant avatar and information
     */
    private Footer createAssistantFooter() {
        Footer footer = new Footer();
        footer.addClassNames(UIConstants.CSS_DRAWER_FOOTER, UIConstants.CSS_SIDEBAR_FOOTER);
        
        Avatar assistantAvatar = new Avatar("A");
        assistantAvatar.addClassNames(UIConstants.CSS_ASSISTANT_AVATAR, UIConstants.CSS_USER_INITIAL);
        assistantAvatar.setHeight("65px");
        assistantAvatar.setWidth("65px");
        
        VerticalLayout assistantInfo = new VerticalLayout();
        assistantInfo.setPadding(false);
        assistantInfo.setSpacing(false);
        assistantInfo.addClassName(UIConstants.CSS_ASSISTANT_TEXT);
        
        H3 assistantName = new H3("Answer42");
        assistantName.addClassName(UIConstants.CSS_ASSISTANT_NAME);
        
        Span assistantRole = new Span("Research Assistant");
        assistantRole.addClassName(UIConstants.CSS_ASSISTANT_NAME);
        
        assistantInfo.add(assistantName, assistantRole);
        
        HorizontalLayout footerLayout = new HorizontalLayout(assistantAvatar, assistantInfo);
        footerLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        footerLayout.addClassName(UIConstants.CSS_ASSISTANT_CONTAINER);
        footerLayout.setSpacing(true);
        footer.add(footerLayout);
        
        return footer;
    }
    
    /**
     * Handles user logout.
     * 
     * Note: Proper order is critical to avoid NullPointerException. 
     * We must navigate first, then clear security context, and optionally invalidate session last.
     */
    private void logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LoggingUtil.debug(LOG, "logout", "Logging out user: %s", 
                auth != null ? auth.getName() : "unknown");
        
        try {
            // First, get a reference to the UI before any security context changes
            UI ui = UI.getCurrent();
            if (ui == null) {
                LoggingUtil.warn(LOG, "logout", "UI is null, cannot navigate.");
                return;
            }
            
            // Logout with the service (clears security context & localStorage token, but NOT session)
            authenticationService.logout();
            
            // Redirect to login page
            ui.navigate(UIConstants.ROUTE_LOGIN);
            LoggingUtil.info(LOG, "logout", "User logged out and redirected to login page");
            
            // Optionally add a small delay for navigation and page load before session invalidation
            // Uncomment if needed:
            // ui.access(() -> {
            //     authenticationService.invalidateSession();
            // });
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "logout", "Error during logout", e);
            // Try to recover by navigating to login page
            try {
                UI ui = UI.getCurrent();
                if (ui != null) {
                    ui.navigate(UIConstants.ROUTE_LOGIN);
                }
            } catch (Exception ne) {
                LoggingUtil.error(LOG, "logout", "Could not navigate after error", ne);
            }
        }
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        // Use the super implementation to let AppLayout handle the content properly
        super.showRouterLayoutContent(content);
        
        // Add additional classes to the content container if needed
        if (content != null && content instanceof Component) {
            Component component = (Component) content;
            component.getElement().getClassList().add("main-content");
            component.getElement().getClassList().add("content");
        }
    }
    
    /**
     * Get the current authenticated user from the Vaadin session.
     * This static method can be called from any view to get the current user.
     * 
     * @return the current user or null if not authenticated
     */
    public static User getCurrentUser() {
        return (User) com.vaadin.flow.server.VaadinSession.getCurrent().getAttribute("currentUser");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        LoggingUtil.debug(LOG, "beforeEnter", "Checking authentication");

        // Skip authentication check for login and register pages
        String path = event.getLocation().getPath();
        if (path.equals(UIConstants.ROUTE_LOGIN) || path.equals(UIConstants.ROUTE_REGISTER)) {
            return;
        }

        // Get authentication from the security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // First, check if the user is authenticated at all
        if (auth == null || "anonymousUser".equals(auth.getPrincipal().toString())) {
            LoggingUtil.debug(LOG, "beforeEnter", "Unauthenticated user, redirecting to login");
            event.forwardTo(UIConstants.ROUTE_LOGIN);
            return;
        }
        
        // Then, check if the user has the required role
        boolean hasUserRole = auth.getAuthorities().stream()
                .anyMatch(grantedAuth -> "ROLE_USER".equals(grantedAuth.getAuthority()));
                
        if (!hasUserRole) {
            LoggingUtil.info(LOG, "beforeEnter", "User lacks required role, redirecting to login");
            event.forwardTo(UIConstants.ROUTE_LOGIN);
            return;
        }
        
        // Check if we already have a user in the session
        currentUser = getCurrentUser();
        
        // If not in session, load from database
        if (currentUser == null) {
            LoggingUtil.debug(LOG, "beforeEnter", "User not in session, loading from database");
            try {
                // Load the user from the database
                currentUser = userService.findByUsername(auth.getName()).orElse(null);
                
                if (currentUser != null) {
                    LoggingUtil.info(LOG, "beforeEnter", "User loaded from database: %s (ID: %s)", 
                        currentUser.getUsername(), currentUser.getId());
                    
                    // Store in session for future requests
                    com.vaadin.flow.server.VaadinSession.getCurrent().setAttribute("currentUser", currentUser);
                } else {
                    LoggingUtil.warn(LOG, "beforeEnter", "Could not find user with username: %s", auth.getName());
                    event.forwardTo(UIConstants.ROUTE_LOGIN);
                }
            } catch (Exception e) {
                LoggingUtil.error(LOG, "beforeEnter", "Error loading user from database", e);
                event.forwardTo(UIConstants.ROUTE_LOGIN);
            }
        } else {
            LoggingUtil.debug(LOG, "beforeEnter", "Using cached user from session: %s", currentUser.getUsername());
        }
    }
}
