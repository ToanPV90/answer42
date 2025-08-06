# Layouts and Navigation

This document covers Vaadin's layout components and navigation patterns, essential for building Answer42's main application structure.

## 📋 Table of Contents

1. [AppLayout - Main Application Layout](#applayout---main-application-layout)
2. [Side Navigation](#side-navigation)
3. [Routing and @Route](#routing-and-route)
4. [RouterLink Navigation](#routerlink-navigation)
5. [Nested Layouts](#nested-layouts)
6. [Form Layout](#form-layout)
7. [Split Layout](#split-layout)
8. [Best Practices](#best-practices)

---

## AppLayout - Main Application Layout

AppLayout provides the foundation for your application's structure with drawer and navbar slots.

### Basic AppLayout Setup

**Java (Flow):**
```java
@Layout
public class MainLayout extends AppLayout {
    public MainLayout() {
        SideNav nav = new SideNav();
        addToDrawer(nav);
    }
}
```

**TypeScript (Hilla/React):**
```tsx
export default function MainLayout() {
    return (
        <AppLayout>
            <SideNav slot="drawer">
                ...
            </SideNav>
            <Outlet />
        </AppLayout>
    );
}
```

**HTML:**
```html
<vaadin-app-layout>
    <vaadin-side-nav slot="drawer">
        ...
    </vaadin-side-nav>
</vaadin-app-layout>
```

### AppLayout with Drawer and Navbar

**Java:**
```java
@Layout
public class MainLayout extends AppLayout {
    public MainLayout() {
        // Create navigation
        SideNav nav = createSideNav();
        addToDrawer(nav);
        
        // Add drawer toggle to navbar
        DrawerToggle toggle = new DrawerToggle();
        addToNavbar(toggle);
        
        // Add title
        H1 title = new H1("Answer42");
        addToNavbar(title);
    }
    
    private SideNav createSideNav() {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Dashboard", "/dashboard"));
        nav.addItem(new SideNavItem("Papers", "/papers"));
        nav.addItem(new SideNavItem("Projects", "/projects"));
        nav.addItem(new SideNavItem("Chat", "/chat"));
        return nav;
    }
}
```

### CSS Custom Properties

```css
/* AppLayout styling */
vaadin-app-layout {
    --vaadin-app-layout-drawer-width: 16em;
}
```

---

## Side Navigation

SideNav provides hierarchical navigation menus with support for nested items.

### Basic Side Navigation

**Java:**
```java
SideNav nav = new SideNav();
nav.addItem(new SideNavItem("Home", "/"));
nav.addItem(new SideNavItem("Papers", "/papers"));
nav.addItem(new SideNavItem("Projects", "/projects"));
```

**TypeScript:**
```tsx
<SideNav onNavigate={({path}) => path && navigate(path)} location={location}>
    <SideNavItem path="/dashboard">Dashboard</SideNavItem>
    <SideNavItem path="/papers">Papers</SideNavItem>
    <SideNavItem path="/projects">Projects</SideNavItem>
</SideNav>
```

### Side Navigation with Icons

**Java:**
```java
private SideNavItem createSideNavItem(MenuEntry menuEntry) {
    if (menuEntry.icon() != null) {
        return new SideNavItem(menuEntry.title(), menuEntry.path(), 
            new Icon(menuEntry.icon()));
    } else {
       return new SideNavItem(menuEntry.title(), menuEntry.path());
    }
}
```

### Nested Path Matching

**Java:**
```java
SideNavItem item = new SideNavItem("Users", "/users");
item.setMatchNested(true); // Matches /users/123, /users/edit, etc.
```

**HTML:**
```html
<vaadin-side-nav-item path="/users" match-nested>
    Users
</vaadin-side-nav-item>
```

---

## Routing and @Route

Vaadin's routing system maps URLs to views using annotations.

### Basic Route Definition

**Java:**
```java
@Route("papers")
public class PapersView extends Div {
    public PapersView() {
        add(new H1("Papers"));
    }
}
```

### Route with Layout

**Java:**
```java
@Route(value = "papers", layout = MainLayout.class)
public class PapersView extends Div {
    public PapersView() {
        add(new H1("Papers"));
    }
}
```

### Route with Parameters

**Java:**
```java
@Route("papers/:paperId")
public class PaperDetailView extends Div implements HasUrlParameter<String> {
    
    @Override
    public void setParameter(BeforeEvent event, String paperId) {
        // Load paper by ID
        Paper paper = paperService.findById(paperId);
        displayPaper(paper);
    }
}
```

### Route Prefixes

**Java:**
```java
@RoutePrefix("admin")
@ParentLayout(MainLayout.class)
public class AdminLayout extends Div implements RouterLayout {
    // All child routes will have /admin prefix
}

@Route(value = "users", layout = AdminLayout.class)
public class AdminUsersView extends Div {
    // URL: /admin/users
}
```

### Absolute Route Prefixes

**Java:**
```java
@RoutePrefix(value = "api", absolute = true)
@ParentLayout(MainLayout.class)
public class ApiLayout extends Div implements RouterLayout {
    // Ignores parent prefixes
}
```

---

## RouterLink Navigation

RouterLink creates navigation links that work with Vaadin's routing system.

### Basic RouterLink

**Java:**
```java
RouterLink link = new RouterLink("Papers", PapersView.class);
add(link);
```

### RouterLink with Parameters

**Java:**
```java
RouterLink link = new RouterLink("Edit Paper", PaperEditView.class, paperId);
add(link);
```

### RouterLink with Icon

**Java:**
```java
Icon homeIcon = new Icon(VaadinIcon.HOME);
RouterLink link = new RouterLink(HomeView.class);
link.add(homeIcon);
add(link);
```

### Programmatic Navigation

**Java:**
```java
Button button = new Button("Go to Papers");
button.addClickListener(e -> 
    getUI().ifPresent(ui -> ui.navigate("papers"))
);
```

**With Parameters:**
```java
Button editButton = new Button("Edit Paper");
editButton.addClickListener(e ->
    getUI().ifPresent(ui -> ui.navigate(PaperEditView.class, paperId))
);
```

---

## Nested Layouts

Create hierarchical layouts for complex applications.

### Parent Layout Definition

**Java:**
```java
@Layout
@ParentLayout(MainLayout.class)
public class AdminLayout extends Div implements RouterLayout {
    public AdminLayout() {
        add(new H2("Administration"));
        // Add admin-specific navigation
    }
}
```

### Child Views

**Java:**
```java
@Route(value = "users", layout = AdminLayout.class)
public class AdminUsersView extends Div {
    // Rendered inside AdminLayout, which is inside MainLayout
}
```

### Custom Content Positioning

**Java:**
```java
public class CustomLayout extends Div implements RouterLayout {
    private Div contentArea = new Div();
    
    public CustomLayout() {
        add(new Header("Header Content"));
        add(contentArea);
        add(new Footer("Footer Content"));
    }
    
    @Override
    public void showRouterLayoutContent(HasElement content) {
        if (content != null) {
            contentArea.getElement().appendChild(content.getElement());
        }
    }
}
```

---

## Form Layout

FormLayout creates responsive forms that adapt to screen size.

### Basic Form Layout

**Java:**
```java
FormLayout formLayout = new FormLayout();
TextField firstName = new TextField("First name");
TextField lastName = new TextField("Last name");
EmailField email = new EmailField("Email");

formLayout.add(firstName, lastName, email);
```

**TypeScript:**
```tsx
<FormLayout>
    <TextField label="First name" />
    <TextField label="Last name" />
    <TextField label="Email" />
</FormLayout>
```

### Responsive Form Layout

**Java:**
```java
formLayout.setResponsiveSteps(
    new ResponsiveStep("0", 1),      // 1 column on small screens
    new ResponsiveStep("320px", 2),  // 2 columns on medium screens
    new ResponsiveStep("500px", 3)   // 3 columns on large screens
);
```

**TypeScript:**
```tsx
<FormLayout responsiveSteps={[
    { minWidth: "0", columns: 1 },
    { minWidth: "320px", columns: 2 },
    { minWidth: "500px", columns: 3 }
]}>
```

### Labels Aside

**Java:**
```java
FormLayout formLayout = new FormLayout();
formLayout.addFormItem(firstName, "First name");
formLayout.addFormItem(lastName, "Last name");
formLayout.setLabelPosition(FormLayout.LabelPosition.ASIDE);
```

### Form Layout Configuration

**Java:**
```java
// Auto-responsive mode
formLayout.setAutoResponsive(true);

// Fixed column width
formLayout.setColumnWidth("8em");

// Maximum columns
formLayout.setMaxColumns(3);

// Expand columns to fill space
formLayout.setColumnGrow(true);

// Expand fields within columns
formLayout.setFieldsExpand(true);
```

### CSS Custom Properties

```css
vaadin-form-layout {
    --vaadin-form-layout-column-spacing: var(--lumo-space-l);
    --vaadin-form-layout-row-spacing: 0;
    --vaadin-form-layout-label-spacing: 1em;
    --vaadin-form-layout-label-width: 8em;
}
```

---

## Split Layout

SplitLayout divides content into resizable areas.

### Basic Split Layout

**Java:**
```java
SplitLayout splitLayout = new SplitLayout();
splitLayout.addToPrimary(new Div(new Text("Primary content")));
splitLayout.addToSecondary(new Div(new Text("Secondary content")));
```

**TypeScript:**
```tsx
<SplitLayout>
    <div>Primary content</div>
    <div>Secondary content</div>
</SplitLayout>
```

**HTML:**
```html
<vaadin-split-layout>
    <div>Primary content area</div>
    <div>Secondary content area</div>
</vaadin-split-layout>
```

---

## Best Practices

### 1. Layout Hierarchy

```java
// Recommended structure for Answer42
MainLayout (AppLayout)
├── DashboardView
├── PapersLayout (nested layout)
│   ├── PapersListView
│   ├── PaperDetailView
│   └── PaperEditView
├── ProjectsLayout (nested layout)
│   ├── ProjectsListView
│   └── ProjectDetailView
└── ChatView
```

### 2. Navigation Menu Structure

**Java:**
```java
private SideNav createMainNavigation() {
    SideNav nav = new SideNav();
    
    // Main sections
    nav.addItem(new SideNavItem("Dashboard", "/dashboard", 
        new Icon(VaadinIcon.DASHBOARD)));
    nav.addItem(new SideNavItem("Papers", "/papers", 
        new Icon(VaadinIcon.BOOK)));
    nav.addItem(new SideNavItem("Projects", "/projects", 
        new Icon(VaadinIcon.FOLDER)));
    nav.addItem(new SideNavItem("AI Chat", "/chat", 
        new Icon(VaadinIcon.CHAT)));
    
    // User section
    nav.addItem(new SideNavItem("Profile", "/profile", 
        new Icon(VaadinIcon.USER)));
    nav.addItem(new SideNavItem("Settings", "/settings", 
        new Icon(VaadinIcon.COG)));
    
    return nav;
}
```

### 3. Responsive Design

```java
// Use responsive steps for forms
formLayout.setResponsiveSteps(
    new ResponsiveStep("0", 1),        // Mobile: 1 column
    new ResponsiveStep("600px", 2),    // Tablet: 2 columns
    new ResponsiveStep("1024px", 3)    // Desktop: 3 columns
);
```

### 4. Layout Performance

```java
// Lazy load content in nested layouts
@Override
public void showRouterLayoutContent(HasElement content) {
    if (content != null) {
        // Clear previous content
        contentArea.removeAll();
        // Add new content
        contentArea.getElement().appendChild(content.getElement());
    }
}
```

### 5. CSS Full Viewport Layout

```css
/* Ensure full viewport usage */
body, #outlet { 
    height: 100vh; 
    width: 100%; 
    margin: 0; 
}
```

---

## Answer42 Integration Examples

### Main Layout for Answer42

**Java:**
```java
@Layout
public class MainLayout extends AppLayout {
    
    public MainLayout() {
        createHeader();
        createDrawer();
    }
    
    private void createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        H1 title = new H1("Answer42");
        title.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);
        
        addToNavbar(toggle, title);
    }
    
    private void createDrawer() {
        SideNav nav = new SideNav();
        
        nav.addItem(new SideNavItem("Dashboard", "/dashboard", 
            new Icon(VaadinIcon.DASHBOARD)));
        nav.addItem(new SideNavItem("Papers", "/papers", 
            new Icon(VaadinIcon.BOOK)));
        nav.addItem(new SideNavItem("Projects", "/projects", 
            new Icon(VaadinIcon.FOLDER)));
        nav.addItem(new SideNavItem("AI Chat", "/chat", 
            new Icon(VaadinIcon.CHAT)));
        nav.addItem(new SideNavItem("Credits", "/credits", 
            new Icon(VaadinIcon.COIN_PILES)));
        
        // User section
        nav.addItem(new SideNavItem("Profile", "/profile", 
            new Icon(VaadinIcon.USER)));
        nav.addItem(new SideNavItem("Settings", "/settings", 
            new Icon(VaadinIcon.COG)));
        nav.addItem(new SideNavItem("Subscription", "/subscription", 
            new Icon(VaadinIcon.CREDIT_CARD)));
        
        addToDrawer(nav);
    }
}
```

### Papers Section Layout

**Java:**
```java
@RoutePrefix("papers")
@ParentLayout(MainLayout.class)
public class PapersLayout extends Div implements RouterLayout {
    
    public PapersLayout() {
        addClassNames(LumoUtility.Padding.MEDIUM);
        
        // Secondary navigation for papers section
        Tabs tabs = new Tabs();
        tabs.add(
            new Tab(new RouterLink("All Papers", PapersView.class)),
            new Tab(new RouterLink("Upload", UploadPaperView.class)),
            new Tab(new RouterLink("Bulk Upload", BulkUploadView.class))
        );
        
        add(tabs);
    }
}
```

---

*This documentation provides the foundation for building Answer42's navigation and layout structure using Vaadin's powerful layout components.*
