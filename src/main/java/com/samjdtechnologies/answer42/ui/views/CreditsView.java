package com.samjdtechnologies.answer42.ui.views;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.annotation.Secured;

import com.samjdtechnologies.answer42.model.CreditBalance;
import com.samjdtechnologies.answer42.model.CreditTransaction;
import com.samjdtechnologies.answer42.model.CreditTransaction.TransactionType;
import com.samjdtechnologies.answer42.model.User;
import com.samjdtechnologies.answer42.service.CreditService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.ui.layout.MainLayout;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Credits view for managing and viewing AI credits.
 */
@Route(value = UIConstants.ROUTE_CREDITS, layout = MainLayout.class)
@PageTitle("Credits | Answer42")
@Secured("ROLE_USER")
public class CreditsView extends VerticalLayout implements BeforeEnterObserver {
    
    private static final Logger LOG = LoggerFactory.getLogger(CreditsView.class);
    
    private final CreditService creditService;
    
    private User currentUser;
    private Grid<CreditTransaction> transactionsGrid;
    
    public CreditsView(CreditService creditService) {
        this.creditService = creditService;
        
        addClassName(UIConstants.CSS_CREDITS_VIEW);
        getStyle().setHeight("auto");
        
        LoggingUtil.debug(LOG, "CreditsView", "CreditsView initialized");
    }
    
    private void initializeView() {
        // Configure the view
        removeAll();

        // Create page header
        add(createWelcomeSection(),
            createCreditBalanceSection(getUserCreditBalance()),
            createHowCreditsWorkSection(),
            createTransactionsSection(getUserTransactions())
        );
    }

    private Component createWelcomeSection() {
        Div section = new Div();
        section.addClassName(UIConstants.CSS_WELCOME_SECTION);

        H1 welcomeTitle = new H1("My Credits");
        welcomeTitle.addClassName(UIConstants.CSS_WELCOME_TITLE);
        
        Paragraph welcomeSubtitle = new Paragraph("Manage Your Credits and Balance History.");
        welcomeSubtitle.addClassName(UIConstants.CSS_WELCOME_SUBTITLE);
        
        section.add(welcomeTitle, welcomeSubtitle);
        return section;
    }
    
    
    private Component createCreditBalanceSection(CreditBalance balance) {
        LoggingUtil.debug(LOG, "createCreditBalanceSection", "Creating credit balance section");
        
        Div section = new Div();
        section.addClassName(UIConstants.CSS_CREDIT_BALANCE_SECTION);
        section.setWidthFull();
        
        H3 sectionTitle = new H3("Credit Balance");
        sectionTitle.addClassName(UIConstants.CSS_SECTION_TITLE);
        
        HorizontalLayout balanceLayout = new HorizontalLayout();
        balanceLayout.setWidthFull();
        balanceLayout.setPadding(true);
        balanceLayout.addClassName(UIConstants.CSS_BALANCE_LAYOUT);
        
        // Balance display
        Div balanceDisplay = new Div();
        balanceDisplay.addClassName(UIConstants.CSS_BALANCE_DISPLAY);
        
        Span balanceValue = new Span(balance != null ? balance.getBalance().toString() : "0");
        balanceValue.addClassName(UIConstants.CSS_BALANCE_VALUE);
        
        Span balanceLabel = new Span(" credits available");
        
        balanceDisplay.add(balanceValue, balanceLabel);
        
        // Usage progress
        VerticalLayout progressLayout = new VerticalLayout();
        progressLayout.setSpacing(false);
        progressLayout.setPadding(false);
        
        Span usageLabel = new Span(
            balance != null ? balance.getUsedThisPeriod() + " used this period" : "0 used this period");
        usageLabel.addClassName(UIConstants.CSS_USAGE_LABEL);
        
        ProgressBar progressBar = new ProgressBar();
        progressBar.setMin(0);
        int maxCredits = 100; // Default value
        int usedCredits = balance != null ? balance.getUsedThisPeriod() : 0;
        progressBar.setMax(maxCredits);
        progressBar.setValue(usedCredits);
        progressBar.addClassName(UIConstants.CSS_CREDIT_PROGRESS);
        
        Span resetLabel = new Span("Next reset: " + 
            (balance != null ? formatResetDate(balance.getNextResetDate()) : "N/A"));
        resetLabel.addClassName(UIConstants.CSS_RESET_LABEL);
        
        progressLayout.add(usageLabel, progressBar, resetLabel);
        
        balanceLayout.add(balanceDisplay, progressLayout);
        
        // Description
        Paragraph description = new Paragraph(
            "Credits are used for AI operations such as paper uploads, summaries, and research queries. " +
            "Your subscription plan will renew your credits on the 1st of each month.");
        description.addClassName(UIConstants.CSS_BALANCE_DESCRIPTION);
        
        // Add components to section
        section.add(sectionTitle, balanceLayout, description);
        
        // Purchase credits button
        Button purchaseButton = new Button("Purchase Credits", new Icon(VaadinIcon.CART));
        purchaseButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        purchaseButton.addClickListener(e -> onPurchaseClicked());
        
        section.add(purchaseButton);
        
        return section;
    }
    
    private Component createHowCreditsWorkSection() {
        LoggingUtil.debug(LOG, "createHowCreditsWorkSection", "Creating how credits work section");
        
        Div section = new Div();
        section.addClassName(UIConstants.CSS_HOW_CREDITS_WORK_SECTION);
        section.setWidthFull();
        
        H3 sectionTitle = new H3("How Credits Work");
        sectionTitle.addClassName(UIConstants.CSS_SECTION_TITLE);
        
        HorizontalLayout columns = new HorizontalLayout();
        columns.setWidthFull();
        
        // Earning Credits column
        VerticalLayout earningLayout = new VerticalLayout();
        earningLayout.setSpacing(false);
        earningLayout.setPadding(false);
        earningLayout.setWidth("50%");
        
        H3 earningTitle = new H3("Earning Credits");
        earningTitle.addClassName(UIConstants.CSS_COLUMN_TITLE);
        
        UnorderedList earningList = new UnorderedList();
        earningList.add(
            new ListItem("Monthly allocation based on your subscription plan"),
            new ListItem("Purchasing additional credit packages"),
            new ListItem("Referral bonuses and special promotions")
        );
        
        earningLayout.add(earningTitle, earningList);
        
        // Using Credits column
        VerticalLayout usingLayout = new VerticalLayout();
        usingLayout.setSpacing(false);
        usingLayout.setPadding(false);
        usingLayout.setWidth("50%");
        
        H3 usingTitle = new H3("Using Credits");
        usingTitle.addClassName(UIConstants.CSS_COLUMN_TITLE);
        
        UnorderedList usingList = new UnorderedList();
        usingList.add(
            new ListItem("Uploading and processing papers (5 credits)"),
            new ListItem("Generating paper summaries (2 credits)"),
            new ListItem("Creating study guides and Q&A sessions (5 credits)"),
            new ListItem("Using research tools with Perplexity (3 credits)")
        );
        
        Paragraph premiumNote = new Paragraph("Premium AI tier costs more but delivers higher quality results");
        premiumNote.addClassName(UIConstants.CSS_PREMIUM_NOTE);
        
        usingLayout.add(usingTitle, usingList, premiumNote);
        
        columns.add(earningLayout, usingLayout);
        section.add(sectionTitle, columns);
        
        return section;
    }
    
    private Component createTransactionsSection(List<CreditTransaction> transactions) {
        LoggingUtil.debug(LOG, "createTransactionsSection", "Creating transactions section");
        
        Div section = new Div();
        section.addClassName(UIConstants.CSS_TRANSACTIONS_SECTION);
        section.setWidthFull();
        
        H3 sectionTitle = new H3("Credit Transactions");
        sectionTitle.addClassName(UIConstants.CSS_SECTION_TITLE);
        
        // Create transactions grid
        transactionsGrid = new Grid<>();
        transactionsGrid.addClassName(UIConstants.CSS_TRANSACTIONS_GRID);
        transactionsGrid.setItems(transactions);
        transactionsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        
        // Define columns
        transactionsGrid.addColumn(transaction -> {
            LocalDateTime date = transaction.getCreatedAt();
            return date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        }).setHeader("Date").setKey("date").setAutoWidth(true);
        
        transactionsGrid.addColumn(transaction -> {
            return transaction.getTransactionType().toString().replace("_", " ");
        }).setHeader("Type").setKey("type").setAutoWidth(true);
        
        transactionsGrid.addColumn(CreditTransaction::getDescription)
            .setHeader("Description").setKey("description").setFlexGrow(1);
        
        transactionsGrid.addColumn(new ComponentRenderer<>(transaction -> {
            Span amount = new Span(formatAmount(transaction.getAmount()));
            String className = transaction.getAmount() >= 0 ? 
                UIConstants.CSS_POSITIVE_AMOUNT : UIConstants.CSS_NEGATIVE_AMOUNT;
            amount.addClassName(className);
            return amount;
        })).setHeader("Amount").setKey("amount").setAutoWidth(true);
        
        transactionsGrid.addColumn(CreditTransaction::getBalanceAfter)
            .setHeader("Balance").setKey("balance").setAutoWidth(true);
        
        // Empty state message
        if (transactions.isEmpty()) {
            Html emptyText = new Html("<div class='" + UIConstants.CSS_EMPTY_TRANSACTIONS + "'>No transactions found</div>");
            section.add(sectionTitle, emptyText);
        } else {
            section.add(sectionTitle, transactionsGrid);
        }
        
        return section;
    }
    
    private void onPurchaseClicked() {
        LoggingUtil.debug(LOG, "onPurchaseClicked", "Purchase credits button clicked");
        
        ConfirmDialog purchaseDialog = new ConfirmDialog();
        purchaseDialog.setHeader("Purchase Credits");
        purchaseDialog.addClassName(UIConstants.CSS_PURCHASE_CREDITS_DIALOG);
        
        // Create package options
        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);
        
        // Track selected option
        Div[] options = new Div[3];
        int[] selectedOption = {0}; // Use array to make it effectively final for lambda
        
        // Option 1
        options[0] = createPurchaseOption("100 Credits", "$10", false, selectedOption, 0, options);
        
        // Option 2
        options[1] = createPurchaseOption("500 Credits", "$45", true, selectedOption, 1, options);
        
        // Option 3
        options[2] = createPurchaseOption("1000 Credits", "$85", false, selectedOption, 2, options);
        
        content.add(options[0], options[1], options[2]);
        
        // Payment method selection (simplified for now)
        H3 paymentTitle = new H3("Payment Method");
        
        Button cardPaymentButton = new Button("Credit Card", new Icon(VaadinIcon.CREDIT_CARD));
        cardPaymentButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        Button cryptoPaymentButton = new Button("Cryptocurrency (10% discount)", new Icon(VaadinIcon.COIN_PILES));
        
        HorizontalLayout paymentButtons = new HorizontalLayout(cardPaymentButton, cryptoPaymentButton);
        paymentButtons.setSpacing(true);
        
        content.add(paymentTitle, paymentButtons);
        
        purchaseDialog.setText(content);
        
        // Confirm button
        purchaseDialog.setConfirmText("Purchase Now");
        purchaseDialog.setConfirmButtonTheme("primary");
        purchaseDialog.addConfirmListener(event -> {
            // Call the service to add credits
            int creditAmount = selectedOption[0] == 0 ? 100 : (selectedOption[0] == 1 ? 500 : 1000);
            processCreditsPayment(creditAmount);
        });
        
        // Cancel button
        purchaseDialog.setCancelText("Cancel");
        purchaseDialog.addCancelListener(event -> purchaseDialog.close());
        
        purchaseDialog.open();
    }
    
    private Div createPurchaseOption(String amount, String price, boolean bestValue, 
                                    int[] selectedOption, int optionIndex, Div[] options) {
        Div option = new Div();
        option.addClassName(UIConstants.CSS_PURCHASE_OPTION);
        if (bestValue) {
            option.addClassName(UIConstants.CSS_PURCHASE_OPTION_SELECTED);
            selectedOption[0] = optionIndex;
        }
        
        if (bestValue) {
            Span bestValueBadge = new Span("BEST VALUE");
            bestValueBadge.addClassName(UIConstants.CSS_CREDIT_PACKAGE_BEST_VALUE);
            option.add(bestValueBadge);
        }
        
        Span amountSpan = new Span(amount);
        amountSpan.addClassName(UIConstants.CSS_CREDIT_PACKAGE_AMOUNT);
        
        Span priceSpan = new Span(price);
        priceSpan.addClassName(UIConstants.CSS_CREDIT_PACKAGE_PRICE);
        
        option.add(amountSpan, priceSpan);
        
        // Add click listener to select this option
        option.getElement().addEventListener("click", e -> {
            // Deselect all options
            for (Div opt : options) {
                opt.removeClassName(UIConstants.CSS_PURCHASE_OPTION_SELECTED);
            }
            // Select this option
            option.addClassName(UIConstants.CSS_PURCHASE_OPTION_SELECTED);
            selectedOption[0] = optionIndex;
        });
        
        return option;
    }
    
    private void processCreditsPayment(int creditAmount) {
        LoggingUtil.info(LOG, "processCreditsPayment", 
                "Processing credit purchase for user %s: %d credits", 
                currentUser.getUsername(), creditAmount);
        
        // In a real application, this would connect to a payment processor
        // For demo purposes, we'll directly add the credits
        try {
            creditService.addCredits(
                currentUser.getId(), 
                creditAmount, 
                TransactionType.PURCHASE, 
                "Credit package purchase", 
                null);
            
            Notification notification = new Notification(
                "Credits purchased successfully! " + creditAmount + " credits have been added to your account.",
                5000);
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            notification.open();
            
            // Refresh the view to show the updated balance
            refreshView();
            
        } catch (Exception e) {
            LoggingUtil.error(LOG, "processCreditsPayment", "Error processing credit purchase", e);
            
            Notification notification = new Notification(
                "There was an error processing your purchase. Please try again later.",
                5000);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            notification.open();
        }
    }
    
    private void refreshView() {
        removeAll();
        initializeView();
    }
    
    private void onSubscriptionPlansClicked() {
        LoggingUtil.debug(LOG, "onSubscriptionPlansClicked", "Subscription plans button clicked");
        getUI().ifPresent(ui -> ui.navigate(UIConstants.ROUTE_SUBSCRIPTION));
    }
    
    private CreditBalance getUserCreditBalance() {
        LoggingUtil.debug(LOG, "getUserCreditBalance", "Getting user credit balance");
        
        // Get the current user ID
        UUID userId = currentUser.getId();
        return creditService.getCreditBalance(userId);
    }
    
    private List<CreditTransaction> getUserTransactions() {
        LoggingUtil.debug(LOG, "getUserTransactions", "Getting user transactions");
        
        // Get the current user ID
        UUID userId = currentUser.getId();
        return creditService.getUserTransactions(userId);
    }
    
    private String formatResetDate(LocalDateTime date) {
        if (date == null) {
            return "N/A";
        }
        return date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
    }
    
    private String formatAmount(int amount) {
        return (amount >= 0 ? "+" : "") + amount;
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        LoggingUtil.debug(LOG, "beforeEnter", "Getting user from session");
        
        // Get the current user from the session (stored by MainLayout)
        currentUser = MainLayout.getCurrentUser();
        
        if (currentUser != null) {
            LoggingUtil.debug(LOG, "beforeEnter", "Retrieved user from session: %s (ID: %s)", 
                currentUser.getUsername(), currentUser.getId());
            
            // Initialize the view with the user's data
            initializeView();
        } else {
            LoggingUtil.warn(LOG, "beforeEnter", "No user found in session, redirecting to login");
            event.forwardTo(UIConstants.ROUTE_LOGIN);
        }
    }
}
