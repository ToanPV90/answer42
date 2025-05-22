# 2. Frontend Architecture

## 2.1 Overview

Answer42's frontend is built using Vaadin 24.7.3, a Java-based web framework that allows developers to create web applications using Java without writing HTML, CSS, or JavaScript directly. This approach provides a consistent development experience across the entire stack while delivering a responsive, modern web interface.

## 2.2 Architectural Pattern

The frontend follows a component-based architecture that aligns with Vaadin's design principles:

- **Server-Driven UI**: UI components are managed on the server-side
- **Component-Based Structure**: UI built from reusable components
- **Event-Driven Interaction**: Components communicate through events
- **Two-Way Data Binding**: Automatic synchronization between UI and data model

The frontend integrates with the Spring backend through direct service calls, leveraging Spring's dependency injection.

## 2.3 Key Components

### 2.3.1 Views

Views represent complete application screens. Each view in Answer42:

- Extends `Div` class
- Implements `BeforeEnterObserver` interface for navigation logic
- Is annotated with `@Route` for routing configuration
- Has appropriate `@PageTitle` and security annotations

Example view structure:

```java
@Route(value = "papers", layout = MainLayout.class)
@PageTitle("Papers | Answer42")
@PermitAll
public class PapersView extends Div implements BeforeEnterObserver {
    
    private final PaperService paperService;
    private final PapersHelper papersHelper;
    
    public PapersView(PaperService paperService, PapersHelper papersHelper) {
        this.paperService = paperService;
        this.papersHelper = papersHelper;
        
        addClassName("papers-view");
        setSizeFull();
        
        // UI component initialization
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Authentication check
        // View initialization logic
    }
    
    // Component creation and event handling methods
}
```

Key views in the application include:

1. **DashboardView**: Home screen with activity summary and quick access
2. **PapersView**: List and manage uploaded papers
3. **ProjectsView**: Organize papers into research projects
4. **AIChatView**: AI-powered chat interface for paper discussion
5. **UploadPaperView**: Paper upload interface
6. **ProfileView**: User profile management
7. **CreditsView**: Credit management interface
8. **SubscriptionView**: Subscription management
9. **SettingsView**: Application settings

### 2.3.2 View Helpers

Each view has a corresponding helper class that encapsulates UI logic and component creation:

```java
@Component
public class PapersHelper {
    
    private final PaperService paperService;
    
    public PapersHelper(PaperService paperService) {
        this.paperService = paperService;
    }
    
    // Methods for creating UI components
    public Component createPaperCard(Paper paper) {
        // Create and configure paper card component
    }
    
    // Methods for handling UI logic
    public void handlePaperSelection(Paper paper, Consumer<Paper> callback) {
        // Handle paper selection logic
    }
}
```

Helper classes promote code reuse and keep views focused on composition and layout.

### 2.3.3 Layout Components

The application uses a structured layout hierarchy:

- **AppShell**: Root application shell with PWA configuration
- **MainLayout**: Primary application layout with navigation components
- **Content Layouts**: Specialized layouts for different content types

The MainLayout provides the application shell with common elements:

```java
@PageTitle("Answer42")
public class MainLayout extends AppLayout {
    
    private final AuthenticationService authenticationService;
    
    public MainLayout(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        
        createHeader();
        createDrawer();
    }
    
    private void createHeader() {
        // Create application header with logo, navigation, and user menu
    }
    
    private void createDrawer() {
        // Create navigation drawer with menu items
    }
}
```

### 2.3.4 UI Constants

UI constants are centralized in the UIConstants class:

```java
public class UIConstants {
    // Color constants
    public static final String COLOR_PRIMARY = "var(--lumo-primary-color)";
    public static final String COLOR_SUCCESS = "var(--lumo-success-color)";
    public static final String COLOR_ERROR = "var(--lumo-error-color)";
    
    // Size constants
    public static final String SIZE_XS = "var(--lumo-size-xs)";
    public static final String SIZE_S = "var(--lumo-size-s)";
    
    // Icon constants
    public static final String ICON_PAPERS = "vaadin:file-text";
    public static final String ICON_PROJECTS = "vaadin:folder";
    
    // Other UI constants
}
```

These constants ensure consistency across the UI and simplify theme updates.

## 2.4 UI Service Layer

UI-specific services manage UI state and handle interaction with backend services:

```java
@UIScope
@VaadinSessionScoped
public class AuthenticationService {
    
    private final UserService userService;
    private User authenticatedUser;
    
    public AuthenticationService(UserService userService) {
        this.userService = userService;
    }
    
    public boolean signIn(String username, String password) {
        // Authentication logic
        return true;
    }
    
    public void signOut() {
        // Sign out logic
    }
    
    public User getAuthenticatedUser() {
        return authenticatedUser;
    }
    
    // Additional methods
}
```

These services maintain UI state within the Vaadin session scope, preventing state leakage between users.

## 2.5 Navigation and Routing

Vaadin's router handles navigation through annotations and programmatic navigation:

```java
// Annotation-based routing
@Route(value = "papers", layout = MainLayout.class)

// Programmatic navigation
UI.getCurrent().navigate(PapersView.class, paperId);
```

Navigation security is enforced through annotations:

```java
// Public access
@AnonymousAllowed

// Authenticated users only
@PermitAll

// Role-based access
@RolesAllowed({"ADMIN", "USER"})
```

## 2.6 Theming System

Answer42 implements a comprehensive theming system:

```
frontend/
└── styles/
    └── themes/
        └── answer42/
            ├── theme.json            # Theme configuration
            ├── styles.css            # Global variables
            ├── main.css              # Main styles
            ├── answer42-dark.css     # Dark theme styles
            └── components/           # Component-specific styles
                ├── ai-chat.css
                ├── auth-forms.css
                ├── dashboard.css
                ├── main-layout.css
                ├── papers.css
                ├── profile.css
                ├── projects.css
                ├── settings.css
                └── subscription.css
```

The theme is based on Vaadin's Lumo theme with custom styling. CSS imports are configured in the AppShell class:

```java
@PWA(name = "Answer42", shortName = "A42")
@Theme("answer42")
@CssImport("./styles/themes/answer42/main.css")
@CssImport("./styles/themes/answer42/styles.css")
@CssImport("./styles/themes/answer42/components/main-layout.css")
// Additional CSS imports
public class AppShell implements AppShellConfigurator {
    // Configuration
}
```

## 2.7 Progressive Web App Features

Answer42 is configured as a Progressive Web App (PWA):

```java
@PWA(
    name = "Answer42",
    shortName = "A42",
    description = "AI-powered research assistant",
    enableInstallPrompt = true,
    offlinePath = "offline.html",
    offlineResources = {
        "./images/answer42-logo.svg",
        "./styles/themes/answer42/main.css"
    }
)
```

PWA features include:
- Offline capabilities
- Install to home screen
- App-like experience
- Service worker for caching
- Manifest file for configuration

## 2.8 Frontend-Backend Integration

Since Vaadin runs on the server, integration with backend services is seamless:

```java
public class PapersView extends Div implements BeforeEnterObserver {
    
    private final PaperService paperService;
    
    public PapersView(PaperService paperService) {
        this.paperService = paperService;
        // View initialization
    }
    
    private void loadPapers() {
        // Direct service call
        List<Paper> papers = paperService.getPapersForUser(getCurrentUser().getId());
        
        // Update UI with results
        papers.forEach(paper -> {
            Component paperCard = createPaperCard(paper);
            papersList.add(paperCard);
        });
    }
}
```

This tight integration eliminates the need for REST API calls between frontend and backend, simplifying development and improving performance.

## 2.9 Responsive Design

The UI implements responsive design using:

- CSS media queries for breakpoint-specific styling
- Responsive layouts (FlexLayout, HorizontalLayout, VerticalLayout)
- Component-level responsive behavior
- Conditional rendering based on viewport size

```java
// Example of conditional rendering based on viewport size
private void createLayout() {
    boolean isMobile = UI.getCurrent().getPage().getWebBrowser().getScreenWidth() < 768;
    
    if (isMobile) {
        createMobileLayout();
    } else {
        createDesktopLayout();
    }
}
```

## 2.10 Form Handling

Forms leverage Vaadin's data binding capabilities:

```java
public class PaperForm extends FormLayout {
    
    private final Binder<Paper> binder = new Binder<>(Paper.class);
    
    private TextField title = new TextField("Title");
    private TextField authors = new TextField("Authors");
    private TextField journal = new TextField("Journal");
    private IntegerField year = new IntegerField("Year");
    
    public PaperForm() {
        addClassName("paper-form");
        
        add(title, authors, journal, year);
        
        // Data binding
        binder.bindInstanceFields(this);
    }
    
    public void setPaper(Paper paper) {
        binder.setBean(paper);
    }
    
    public Paper getPaper() {
        return binder.getBean();
    }
}
```

## 2.11 Error Handling

The frontend implements error handling at multiple levels:

- **Global Error View**: Custom error view for navigation errors
- **Component-Level Error States**: Error states for UI components
- **Form Validation**: Client and server-side validation
- **Notification System**: User-friendly error notifications

```java
// Example of error notification
private void showError(String message) {
    Notification notification = new Notification(
        message,
        5000,
        Notification.Position.BOTTOM_CENTER
    );
    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    notification.open();
}
```

## 2.12 Accessibility Features

The UI includes several accessibility features:

- ARIA attributes for screen readers
- Keyboard navigation support
- Consistent focus management
- Adequate color contrast
- Responsive text sizing
