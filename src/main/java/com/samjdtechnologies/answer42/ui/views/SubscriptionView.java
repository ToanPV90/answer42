package com.samjdtechnologies.answer42.ui.views;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.samjdtechnologies.answer42.model.SubscriptionPlan;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.service.SubscriptionService;
import com.samjdtechnologies.answer42.service.UserService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * SubscriptionView displays available subscription plans and allows users to manage their subscriptions.
 */
@Route(value = UIConstants.ROUTE_SUBSCRIPTION, layout = MainLayout.class)
@PageTitle("Answer42 - Subscription")
@Secured("ROLE_USER")
public class SubscriptionView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionView.class);
    
    private final SubscriptionService subscriptionService;
    private final UserService userService;
    
    private User currentUser;
    private SubscriptionPlan currentPlan;
    private boolean isYearly = false;
    
    private HorizontalLayout plansContainer;
    private Span monthlyLabel;
    private Span yearlyLabel;
    private Checkbox periodToggle;
    
    public SubscriptionView(SubscriptionService subscriptionService, UserService userService) {
        this.subscriptionService = subscriptionService;
        this.userService = userService;
        
        addClassName(UIConstants.CSS_SUBSCRIPTION_VIEW);
        
        LoggingUtil.debug(LOG, "SubscriptionView", "SubscriptionView initialized");
    }

    private void initializeView() {
        // Title and description
        H1 title = new H1("Choose Your Plan");
        title.addClassName(UIConstants.CSS_SUBSCRIPTION_TITLE);
        
        Paragraph description = new Paragraph();
        description.add("Select the plan that best fits your research needs. All plans include core features with varying usage limits.");
        description.addClassName(UIConstants.CSS_SUBSCRIPTION_DESCRIPTION);
        
        // Bitcoin payment banner
        Component bitcoinBanner = createBitcoinBanner();
        
        // Billing period toggle
        Component billingToggle = createBillingToggle();
        
        // Plans container
        plansContainer = new HorizontalLayout();
        plansContainer.addClassName(UIConstants.CSS_PLAN_CARDS_CONTAINER);
        
        // Add the subscription plans
        populateSubscriptionPlans();
        
        // FAQ section
        Component faqSection = createFaqSection();
        
        // Add all components to the view
        add(title, description, bitcoinBanner, billingToggle, plansContainer, faqSection);
    }
    
    private Component createBitcoinBanner() {
        HorizontalLayout banner = new HorizontalLayout();
        banner.addClassName(UIConstants.CSS_BITCOIN_BANNER);
        
        Icon bitcoinIcon = VaadinIcon.COIN_PILES.create();
        bitcoinIcon.addClassName(UIConstants.CSS_BITCOIN_ICON);
        
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        
        H3 heading = new H3("Pay with Bitcoin and Save!");
        
        Paragraph info = new Paragraph();
        info.add("Get an additional 20% discount when you pay with Bitcoin. Combined with annual billing, save up to 40%! Cryptocurrency payments are processed securely through our payment partner.");
        
        content.add(heading, info);
        banner.add(bitcoinIcon, content);
        
        return banner;
    }
    
    private Component createBillingToggle() {
        HorizontalLayout toggleContainer = new HorizontalLayout();
        toggleContainer.addClassName(UIConstants.CSS_TOGGLE_CONTAINER);
        
        monthlyLabel = new Span("Monthly");
        monthlyLabel.addClassName(UIConstants.CSS_TOGGLE_ACTIVE);
        
        yearlyLabel = new Span("Yearly");
        
        periodToggle = new Checkbox();
        periodToggle.addClassName(UIConstants.CSS_PERIOD_TOGGLE);
        
        Span discount = new Span("(20% off)");
        discount.addClassName("discount");
        
        periodToggle.addValueChangeListener(event -> {
            isYearly = event.getValue();
            updateToggleState();
            updatePlanPrices();
        });
        
        toggleContainer.add(monthlyLabel, periodToggle, yearlyLabel, discount);
        return toggleContainer;
    }
    
    private void updateToggleState() {
        if (isYearly) {
            monthlyLabel.removeClassName(UIConstants.CSS_TOGGLE_ACTIVE);
            yearlyLabel.addClassName(UIConstants.CSS_TOGGLE_ACTIVE);
        } else {
            monthlyLabel.addClassName(UIConstants.CSS_TOGGLE_ACTIVE);
            yearlyLabel.removeClassName(UIConstants.CSS_TOGGLE_ACTIVE);
        }
    }
    
    private void populateSubscriptionPlans() {
        plansContainer.removeAll();
        
        List<SubscriptionPlan> plans = subscriptionService.getAllPlans();
        if (plans.isEmpty()) {
            Paragraph noPlans = new Paragraph("No subscription plans are currently available.");
            plansContainer.add(noPlans);
            return;
        }
        
        // Add all plans to the container
        plans.forEach(plan -> {
            Component planCard = createPlanCard(plan);
            plansContainer.add(planCard);
        });
    }
    
    private void updatePlanPrices() {
        populateSubscriptionPlans();
    }
    
    private Component createPlanCard(SubscriptionPlan plan) {
        Div card = new Div();
        card.addClassName(UIConstants.CSS_PLAN_CARD);
        
        boolean isCurrentPlan = currentPlan != null && currentPlan.getId().equals(plan.getId());
        boolean isPopular = "Basic".equalsIgnoreCase(plan.getName());
        
        if (isCurrentPlan) {
            card.addClassName(UIConstants.CSS_CURRENT_PLAN);
        }
        
        if (isPopular) {
            card.addClassName(UIConstants.CSS_POPULAR_PLAN);
        }
        
        // Card Header
        Div header = new Div();
        header.addClassName(UIConstants.CSS_PLAN_CARD_HEADER);
        
        H2 planName = new H2(plan.getName());
        planName.addClassName(UIConstants.CSS_PLAN_CARD_TITLE);
        
        // Format price based on billing period
        BigDecimal price = isYearly ? plan.getPriceAnnually() : plan.getPriceMonthly();
        String priceText = formatPrice(price);
        String period = isYearly ? "/year" : "/month";
        
        HorizontalLayout priceLayout = new HorizontalLayout();
        priceLayout.setPadding(false);
        priceLayout.setSpacing(false);
        
        Span priceSpan = new Span(priceText);
        priceSpan.addClassName(UIConstants.CSS_PLAN_CARD_PRICE);
        
        Span periodSpan = new Span(period);
        periodSpan.addClassName(UIConstants.CSS_PLAN_CARD_PERIOD);
        
        priceLayout.add(priceSpan, periodSpan);
        
        header.add(planName, priceLayout);
        
        // Feature list
        VerticalLayout features = new VerticalLayout();
        features.addClassName(UIConstants.CSS_PLAN_CARD_FEATURES);
        features.setPadding(false);
        features.setSpacing(false);
        
        // Get the features for this plan
        addFeatureItem(features, plan.getBaseCredits() + " credits monthly");
        
        if (plan.getFeatures() != null) {
            // Max paper uploads
            Object maxPapers = plan.getFeatures().get("maxPaperUploads");
            if (maxPapers != null) {
                int maxPapersValue = maxPapers instanceof Number ? ((Number) maxPapers).intValue() : 0;
                String paperText = maxPapersValue < 0 ? "Unlimited papers" : maxPapersValue + " papers max";
                addFeatureItem(features, paperText);
            }
            
            // Max projects
            Object maxProjects = plan.getFeatures().get("maxProjects");
            if (maxProjects != null) {
                int maxProjectsValue = maxProjects instanceof Number ? ((Number) maxProjects).intValue() : 0;
                addFeatureItem(features, maxProjectsValue + " projects max");
            }
            
            // Credits rollover
            Object creditsRollover = plan.getFeatures().get("creditsRollover");
            if (creditsRollover != null) {
                int rolloverValue = creditsRollover instanceof Number ? ((Number) creditsRollover).intValue() : 0;
                addFeatureItem(features, rolloverValue + " credits rollover");
            }
            
            // AI tier
            Object aiTier = plan.getFeatures().get("aiTier");
            if (aiTier != null) {
                String aiTierValue = aiTier.toString();
                String tierText = aiTierValue.substring(0, 1).toUpperCase() + aiTierValue.substring(1) + " AI tier";
                addFeatureItem(features, tierText);
            } else {
                Object aiAnalysis = plan.getFeatures().get("aiAnalysis");
                if (aiAnalysis != null) {
                    String aiAnalysisValue = aiAnalysis.toString();
                    String analysisText = aiAnalysisValue.substring(0, 1).toUpperCase() + aiAnalysisValue.substring(1) + " AI analysis";
                    addFeatureItem(features, analysisText);
                }
            }
            
            // Support
            Object support = plan.getFeatures().get("support");
            if (support != null) {
                String supportValue = support.toString();
                String supportText = supportValue.substring(0, 1).toUpperCase() + supportValue.substring(1) + " support";
                addFeatureItem(features, supportText);
            }
            
            // Team collaboration
            Object teamMembers = plan.getFeatures().get("teamMembers");
            if (teamMembers != null) {
                int teamCount = teamMembers instanceof Number ? ((Number) teamMembers).intValue() : 0;
                addFeatureItem(features, "Team collaboration (" + teamCount + " members)");
            }
            
            // Advanced PDF tools
            Object advancedPdfTools = plan.getFeatures().get("advancedPdfTools");
            if (advancedPdfTools instanceof Boolean && (Boolean) advancedPdfTools) {
                addFeatureItem(features, "Advanced PDF tools");
            }
            
            // Custom integrations
            Object customIntegrations = plan.getFeatures().get("customIntegrations");
            if (customIntegrations instanceof Boolean && (Boolean) customIntegrations) {
                addFeatureItem(features, "Custom integrations");
            }
            
            // Dedicated support
            Object dedicatedSupport = plan.getFeatures().get("dedicatedSupport");
            if (dedicatedSupport instanceof Boolean && (Boolean) dedicatedSupport) {
                addFeatureItem(features, "Dedicated support");
            }
            
            // Offline access
            Object offlineAccess = plan.getFeatures().get("offlineAccess");
            if (offlineAccess instanceof Boolean && (Boolean) offlineAccess) {
                addFeatureItem(features, "Offline access");
            }
        }
        
        // Subscribe button
        Button subscribeButton;
        if (isCurrentPlan) {
            subscribeButton = new Button("Current Plan");
            subscribeButton.addClassName("current");
            subscribeButton.setEnabled(false);
        } else {
            subscribeButton = new Button("Subscribe");
            subscribeButton.addClickListener(e -> handleSubscription(plan));
        }
        
        subscribeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        subscribeButton.addClassName(UIConstants.CSS_PLAN_CARD_BUTTON);
        
        // Add all components to the card
        card.add(header, features, subscribeButton);
        
        return card;
    }
    
    private void addFeatureItem(VerticalLayout container, String featureText) {
        HorizontalLayout featureItem = new HorizontalLayout();
        featureItem.addClassName(UIConstants.CSS_PLAN_CARD_FEATURE);
        featureItem.setSpacing(false);
        featureItem.setPadding(false);
        
        Icon checkIcon = VaadinIcon.CHECK.create();
        checkIcon.addClassName(UIConstants.CSS_FEATURE_ICON);
        
        Span text = new Span(featureText);
        
        featureItem.add(checkIcon, text);
        container.add(featureItem);
    }
    
    private String formatPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return "$0";
        }
        
        return "$" + price.toPlainString();
    }
    
    private void handleSubscription(SubscriptionPlan plan) {
        // This would typically call a payment processor or initiate a subscription
        String message = "Subscription to " + plan.getName() + " plan will be available soon!";
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
        notification.setPosition(Notification.Position.MIDDLE);
        notification.setDuration(3000);
        
        LoggingUtil.info(LOG, "handleSubscription", 
            "User %s attempted to subscribe to plan %s", 
            currentUser.getUsername(), plan.getName());
    }
    
    private Component createFaqSection() {
        Div faqSection = new Div();
        faqSection.addClassName(UIConstants.CSS_SUBSCRIPTION_FAQ);
        
        H2 faqTitle = new H2("Subscription FAQ");
        
        // Create FAQ details components
        Details upgradeDowngrade = createFaqItem(
            "Can I upgrade or downgrade my plan?",
            "Yes, you can upgrade or downgrade your subscription plan at any time. " +
            "Upgrades take effect immediately, while downgrades will take effect at the end of your current billing cycle."
        );
        
        Details creditsSystem = createFaqItem(
            "How does the credits system work?",
            "Each plan comes with a monthly allocation of credits. Credits are used for AI-powered operations " +
            "like generating summaries, research assistance, and advanced analysis. Unused credits from the Basic, " +
            "Pro, and Scholar plans roll over to the next month (up to the specified maximum)."
        );
        
        Details paymentMethods = createFaqItem(
            "What payment methods do you accept?",
            "We accept major credit cards (Visa, Mastercard, American Express), PayPal, and cryptocurrency payments " +
            "(Bitcoin, Ethereum). Cryptocurrency payments qualify for an additional 20% discount."
        );
        
        Details cancelSubscription = createFaqItem(
            "Can I cancel my subscription?",
            "Yes, you can cancel your subscription at any time. You'll continue to have access to your plan's " +
            "features until the end of your current billing cycle, after which your account will revert to the Free plan."
        );
        
        Details dataDowngrade = createFaqItem(
            "What happens to my data if I downgrade?",
            "Your data remains secure in our system even if you downgrade. However, you may lose access to some " +
            "advanced features and exceed your new plan's paper or project limits. In such cases, you won't lose data " +
            "but may need to upgrade again to access all your files."
        );
        
        faqSection.add(
            faqTitle,
            upgradeDowngrade,
            creditsSystem,
            paymentMethods,
            cancelSubscription,
            dataDowngrade
        );
        
        return faqSection;
    }
    
    private Details createFaqItem(String summary, String content) {
        Paragraph contentParagraph = new Paragraph();
        contentParagraph.add(new Text(content));
        
        Details details = new Details(summary, contentParagraph);
        
        return details;
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        LoggingUtil.debug(LOG, "beforeEnter", "Getting authentication");
        
        // Get authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // Check if user is authenticated
        if (auth == null || "anonymousUser".equals(auth.getPrincipal().toString())) {
            LoggingUtil.debug(LOG, "beforeEnter", "Unauthenticated user, redirecting to login");
            event.forwardTo(UIConstants.ROUTE_LOGIN);
            return;
        }
        
        try {
            // Load the current user
            String username = auth.getName();
            Optional<User> userOpt = userService.findByUsername(username);
            
            if (userOpt.isEmpty()) {
                LoggingUtil.warn(LOG, "beforeEnter", "User not found: %s", username);
                event.forwardTo(UIConstants.ROUTE_LOGIN);
                return;
            }
            
            currentUser = userOpt.get();
            
            // Get the user's current subscription plan
            Optional<SubscriptionPlan> planOpt = subscriptionService.getUserCurrentPlan(currentUser.getId());
            currentPlan = planOpt.orElse(null);
            
            // Initialize the view
            initializeView();
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "beforeEnter", "Error loading subscription view", e);
            event.forwardTo(UIConstants.ROUTE_LOGIN);
        }
    }
}
