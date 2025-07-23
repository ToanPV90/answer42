package com.samjdtechnologies.answer42.ui.helpers.views;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;

import com.samjdtechnologies.answer42.model.daos.Paper;
import com.samjdtechnologies.answer42.model.daos.User;
import com.samjdtechnologies.answer42.service.PaperService;
import com.samjdtechnologies.answer42.ui.constants.UIConstants;
import com.samjdtechnologies.answer42.util.LoggingUtil;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.receivers.FileBuffer;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.server.StreamResource;

/**
 * Helper class for PapersView to handle non-UI rendering logic.
 */
public class PapersViewHelper {
    
    private static final Logger LOG = LoggerFactory.getLogger(PapersViewHelper.class);

    /**
     * Update list of papers and refresh the grid based on search and filter criteria.
     * 
     * @param currentUser the user whose papers are being displayed
     * @param searchTerm the search term to filter papers by
     * @param statusValue the status filter to apply
     * @param page the current page number (0-based)
     * @param pageSize the number of items per page
     * @param grid the Grid component to update with papers
     * @param pageInfo the Span component displaying pagination information
     * @param prevButton the Button for navigating to previous page
     * @param nextButton the Button for navigating to next page
     * @param paperService the service for accessing paper data
     * @param pageUpdateCallback callback function to update page when pagination changes
     */
    public static void updateList(User currentUser, 
                               String searchTerm, 
                               String statusValue,
                               int page,
                               int pageSize,
                               Grid<Paper> grid,
                               Span pageInfo,
                               Button prevButton, 
                               Button nextButton,
                               PaperService paperService,
                               Consumer<Integer> pageUpdateCallback) {
        
        LoggingUtil.debug(LOG, "updateList", "Updating with user: " + (currentUser != null ? currentUser.getUsername() : "null") + 
            ", search: '" + searchTerm + "', status: '" + statusValue + "'");
            
        // Use helper to fetch papers list
        Page<Paper> papers = fetchPapersList(
            currentUser, searchTerm, statusValue, page, pageSize, paperService);
        
        // Update grid
        int contentSize = papers.getContent().size();
        LoggingUtil.info(LOG, "updateList", "Setting grid with " + contentSize + " papers");
        grid.setItems(papers.getContent());
        
        // Update pagination and potentially adjust page if beyond bounds
        int adjustedPage = updatePagination(
            papers.getNumber(), papers.getTotalPages(), papers.getTotalElements(), 
            pageInfo, prevButton, nextButton);
        
        // If page changed, update the current page and refresh
        if (adjustedPage != page && pageUpdateCallback != null) {
            LoggingUtil.debug(LOG, "updateList", "Page adjustment needed: " + page + " -> " + adjustedPage);
            pageUpdateCallback.accept(adjustedPage);
        }
    }

    /**
     * Configure the grid with standard paper columns and behaviors.
     * 
     * @param grid the Grid component to configure with paper columns
     * @param actionsRenderer renderer for the actions column
     * @param itemClickHandler callback function to handle double-click on grid rows
     */
    public static void configureGrid(Grid<Paper> grid, 
                                     ComponentRenderer<Component, Paper> actionsRenderer,
                                     Consumer<Paper> itemClickHandler) {
        LoggingUtil.debug(LOG, "configureGrid", "Configuring papers grid...");
        
        grid.addClassName(UIConstants.CSS_PAPERS_GRID);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        // Define columns with optimized widths to fit container
        // Actions column - let CSS control the width
        grid.addColumn(actionsRenderer)
                .setHeader("Actions").setAutoWidth(false).setWidth("200px").setFlexGrow(0);
        
        // Title - allow to grow more
        grid.addColumn(Paper::getTitle).setHeader("Title").setAutoWidth(false).setFlexGrow(3);
        
        // Authors - moderate space
        grid.addColumn(paper -> {
            List<String> authors = paper.getAuthors();
            if (authors != null && !authors.isEmpty()) {
                return String.join(", ", authors);
            }
            return "";
        }).setHeader("Authors").setAutoWidth(false).setFlexGrow(2);
        
        // Journal - moderate space
        grid.addColumn(Paper::getJournal).setHeader("Journal").setAutoWidth(false).setFlexGrow(1);
        
        // Year - fixed small width
        grid.addColumn(Paper::getYear).setHeader("Year").setAutoWidth(false).setWidth("70px").setFlexGrow(0);
        
        // Status column with colored badge - fixed width
        grid.addColumn(new ComponentRenderer<>(paper -> {
            String status = paper.getStatus();
            Span badge = new Span(status);
            badge.getElement().getThemeList().add("badge " + status.toLowerCase());
            return badge;
        })).setHeader("Status").setAutoWidth(false).setWidth("100px").setFlexGrow(0);
        
        // Created date column with formatter - fixed width
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault());
        grid.addColumn(paper -> paper.getCreatedAt() != null ? 
                formatter.format(paper.getCreatedAt()) : "")
                .setHeader("Uploaded").setAutoWidth(false).setWidth("100px").setFlexGrow(0);

        // Add selection listener for double-click
        grid.addItemClickListener(event -> {
            if (event.getClickCount() == 2 && itemClickHandler != null) {
                itemClickHandler.accept(event.getItem());
            }
        });
        
        LoggingUtil.debug(LOG, "configureGrid", "Grid configuration completed");
    }

    /**
     * Updates the grid with papers based on search and filter criteria.
     * 
     * @param currentUser the user whose papers are being fetched
     * @param searchTerm the search term to filter papers by
     * @param statusValue the status filter to apply
     * @param page the current page number (0-based)
     * @param pageSize the number of items per page
     * @param paperService the service for accessing paper data
     * @return a Page object containing papers matching the search criteria and pagination parameters
     */
    public static Page<Paper> fetchPapersList(User currentUser, String searchTerm, String statusValue, 
                                             int page, int pageSize, PaperService paperService) {
        LoggingUtil.debug(LOG, "fetchPapersList", "Fetching papers for user: " + 
            (currentUser != null ? currentUser.getUsername() + " (ID: " + currentUser.getId() + ")" : "null"));
            
        if (currentUser == null) {
            LoggingUtil.error(LOG, "fetchPapersList", "Cannot fetch papers: Current user is null");
            return Page.empty(PageRequest.of(0, pageSize));
        }
        
        try {
            // Ensure params are not null to avoid NPE
            final String finalSearchTerm = searchTerm == null ? "" : searchTerm;
            final String finalStatusValue = statusValue == null ? "All" : statusValue;
            
            LoggingUtil.debug(LOG, "fetchPapersList", 
                "Using parameters: searchTerm='" + finalSearchTerm + 
                "', statusValue='" + finalStatusValue + 
                "', page=" + page +
                ", pageSize=" + pageSize);
            
            // Fetch papers based on filters
            PageRequest pageRequest = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Paper> result;
            
            if (!finalSearchTerm.isEmpty() && !"All".equals(finalStatusValue)) {
                // Search with status filter
                LoggingUtil.debug(LOG, "fetchPapersList", "Using search with status filter");
                Page<Paper> searchResults = paperService.searchPapersByUser(currentUser, finalSearchTerm, pageRequest);
                
                // Since we can't filter a Page directly, we'll load all matching papers and filter
                List<Paper> filteredPapers = searchResults.getContent().stream()
                        .filter(paper -> paper.getStatus().equals(finalStatusValue))
                        .toList();
                
                // Apply pagination manually (this is simplified and not ideal for large datasets)
                result = new PageImpl<>(
                    filteredPapers, 
                    pageRequest, 
                    searchResults.getTotalElements()
                );
            } else if (!finalSearchTerm.isEmpty()) {
                // Search without status filter
                LoggingUtil.debug(LOG, "fetchPapersList", "Using search without status filter");
                result = paperService.searchPapersByUser(currentUser, finalSearchTerm, pageRequest);
            } else if (!"All".equals(finalStatusValue)) {
                // Status filter without search
                LoggingUtil.debug(LOG, "fetchPapersList", "Using status filter without search");
                result = paperService.getPapersByUserAndStatus(currentUser, finalStatusValue, pageRequest);
            } else {
                // No filters
                LoggingUtil.debug(LOG, "fetchPapersList", "No filters, retrieving all user papers");
                result = paperService.getPapersByUser(currentUser, pageRequest);
            }
            
            LoggingUtil.info(LOG, "fetchPapersList", "Retrieved " + result.getContent().size() + 
                " papers for user " + currentUser.getUsername() + " (total: " + result.getTotalElements() + ")");
            
            return result;
        } catch (Exception e) {
            LoggingUtil.error(LOG, "fetchPapersList", "Error fetching papers: " + e.getMessage(), e);
            return Page.empty(PageRequest.of(page, pageSize));
        }
    }

    /**
     * Update pagination information.
     * 
     * @param currentPage the current page number (0-based)
     * @param totalPages the total number of pages
     * @param totalItems the total number of items
     * @param pageInfo the Span component to update with pagination text
     * @param prevButton the Button for navigating to previous page
     * @param nextButton the Button for navigating to next page
     * @return the adjusted page number, which may be different from the current page if
     *         pagination boundaries were exceeded
     */
    public static int updatePagination(int currentPage, int totalPages, long totalItems, Span pageInfo,
                                    Button prevButton, Button nextButton) {
        LoggingUtil.debug(LOG, "updatePagination", "Page: " + (currentPage + 1) + "/" + 
            totalPages + " (Total items: " + totalItems + ")");
            
        pageInfo.setText((currentPage + 1) + " of " + totalPages + " (" + totalItems + " items)");
        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage + 1 < totalPages);
        
        // If current page is beyond total pages, adjust and return new page
        if (totalPages > 0 && currentPage >= totalPages) {
            int adjustedPage = totalPages - 1;
            LoggingUtil.debug(LOG, "updatePagination", "Adjusting page from " + currentPage + " to " + adjustedPage);
            return adjustedPage;
        }
        
        return currentPage;
    }

    /**
     * Validate upload form fields and enable/disable the submit button based on validation results.
     * 
     * @param titleField the text field for paper title
     * @param authorsField the text field for paper authors
     * @param fileUploaded boolean indicating whether a file has been uploaded
     * @param submitButton the submit button to enable/disable based on validation
     */
    public static void validateUploadForm(TextField titleField, TextField authorsField, boolean fileUploaded, Button submitButton) {
        boolean titleValid = !titleField.isEmpty();
        boolean authorsValid = !authorsField.isEmpty();
        
        submitButton.setEnabled(titleValid && authorsValid && fileUploaded);
    }

    /**
     * Create a MultipartFile from a FileBuffer.
     * 
     * @param buffer the FileBuffer containing file data
     * @return a MultipartFile implementation created from the provided buffer
     */
    public static MultipartFile createMultipartFileFromBuffer(FileBuffer buffer) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }
            
            @Override
            public String getOriginalFilename() {
                return buffer.getFileName();
            }
            
            @Override
            public String getContentType() {
                // Determine content type from file name
                String fileName = buffer.getFileName();
                if (fileName.endsWith(".pdf")) {
                    return "application/pdf";
                } else if (fileName.endsWith(".doc")) {
                    return "application/msword";
                } else if (fileName.endsWith(".docx")) {
                    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                } else {
                    return "application/octet-stream";
                }
            }
            
            @Override
            public boolean isEmpty() {
                return buffer.getInputStream() == null;
            }
            
            @Override
            public long getSize() {
                try {
                    return buffer.getInputStream().available();
                } catch (Exception e) {
                    return 0;
                }
            }
            
            @Override
            public byte[] getBytes() throws IOException {
                return buffer.getInputStream().readAllBytes();
            }
            
            @Override
            public InputStream getInputStream() throws IOException {
                return buffer.getInputStream();
            }
            
            @Override
            public void transferTo(File dest) throws IOException, IllegalStateException {
                try (FileOutputStream out = new FileOutputStream(dest)) {
                    out.write(getBytes());
                }
            }
        };
    }

    /**
     * Download a paper by creating a stream resource and triggering download.
     * 
     * @param paper the paper object containing the file path to download
     * @param ui the UI instance to use for download operations
     * @param viewComponent the parent component where download elements will be temporarily added
     */
    public static void downloadPaper(Paper paper, UI ui, HasComponents viewComponent) {
        try {
            String filePath = paper.getFilePath();
            if (filePath != null && !filePath.isEmpty()) {
                File file = new File(filePath);
                if (file.exists()) {
                    // Create a stream resource for the file
                    String fileName = file.getName();
                    StreamResource resource = new StreamResource(fileName, () -> {
                        try {
                            return new ByteArrayInputStream(Files.readAllBytes(Paths.get(filePath)));
                        } catch (Exception e) {
                            Notification.show("Download failed: " + e.getMessage(), 
                                3000, Notification.Position.BOTTOM_START)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                            return new ByteArrayInputStream(new byte[0]);
                        }
                    });
                    
                    // Create an anchor component for download and trigger it
                    Anchor downloadLink = new Anchor(resource, "");
                    downloadLink.setTarget("_blank");
                    downloadLink.getElement().setAttribute("download", true);
                    
                    // Add to UI temporarily and click it
                    if (ui != null) {
                        ui.add(downloadLink);
                        ui.getPage().executeJs("$0.click()", downloadLink.getElement());
                        // Clean up after click handled
                        ui.access(() -> ui.remove(downloadLink));
                    }
                } else {
                    Notification.show("File not found", 3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            } else {
                Notification.show("No file associated with this paper", 
                    3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            }
        } catch (Exception e) {
            LoggingUtil.error(LOG, "downloadPaper", "Download failed: " + e.getMessage(), e);
            Notification.show("Error: " + e.getMessage(), 
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
