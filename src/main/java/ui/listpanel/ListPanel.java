package ui.listpanel;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import ui.UI;
import ui.components.IssueListView;
import ui.components.KeyboardShortcuts;
import ui.issuepanel.FilterPanel;
import ui.issuepanel.PanelControl;
import util.KeyPress;
import util.events.IssueSelectedEvent;
import util.events.ShowLabelPickerEvent;
import util.events.testevents.UIComponentFocusEvent;
import backend.interfaces.IModel;
import backend.resource.TurboIssue;
import filter.expression.Qualifier;

public class ListPanel extends FilterPanel {

    private final IModel model;
    private final UI ui;
    private int issueCount;

    private IssueListView listView;
    private HashMap<Integer, Integer> issueCommentCounts = new HashMap<>();
    private HashMap<Integer, Integer> issueNonSelfCommentCounts = new HashMap<>();

    // Context Menu
    private final ContextMenu contextMenu = new ContextMenu();

    private final MenuItem markAsReadUnreadMenuItem = new MenuItem();
    private static final String markAsReadMenuItemText = "Mark as read (E)";
    private static final String markAsUnreadMenuItemText = "Mark as unread (U)";

    private static final MenuItem changeLabelsMenuItem = new MenuItem();
    private static final String changeLabelsMenuItemText = "Change labels (L)";

    public ListPanel(UI ui, IModel model, PanelControl parentPanelControl, int panelIndex) {
        super(ui, model, parentPanelControl, panelIndex);
        this.model = model;
        this.ui = ui;

        listView = new IssueListView();
        setupListView();
        getChildren().add(listView);
    }

    /**
     * Determines if an issue has had new comments added (or removed) based on
     * its last-known comment count in {@link #issueCommentCounts}.
     * @param issue
     * @return true if the issue has changed, false otherwise
     */
    private boolean issueHasNewComments(TurboIssue issue, boolean hasMetadata) {
        if (currentFilterExpression.getQualifierNames().contains(Qualifier.UPDATED) && hasMetadata) {
            return issueNonSelfCommentCounts.containsKey(issue.getId()) &&
                    Math.abs(
                            issueNonSelfCommentCounts.get(issue.getId()) - issue.getMetadata().getNonSelfCommentCount()
                    ) > 0;
        } else {
            return issueCommentCounts.containsKey(issue.getId()) &&
                    Math.abs(issueCommentCounts.get(issue.getId()) - issue.getCommentCount()) > 0;
        }
    }

    /**
     * Updates {@link #issueCommentCounts} with the latest counts.
     * Returns a list of issues which have new comments.
     * @return
     */
    private HashSet<Integer> updateIssueCommentCounts(boolean hasMetadata) {
        HashSet<Integer> result = new HashSet<>();
        for (TurboIssue issue : getIssueList()) {
            if (issueCommentCounts.containsKey(issue.getId())) {
                // We know about this issue; check if it's been updated
                if (issueHasNewComments(issue, hasMetadata)) {
                    result.add(issue.getId());
                }
            } else {
                // We don't know about this issue, just put the current comment count.
                issueNonSelfCommentCounts.put(issue.getId(), issue.getMetadata().getNonSelfCommentCount());
                issueCommentCounts.put(issue.getId(), issue.getCommentCount());
            }
        }
        return result;
    }

    /**
     * Refreshes the list of issue cards shown to the user.
     *
     * @param hasMetadata Indicates the comment count hashmap to be used.
     */
    @Override
    public void refreshItems(boolean hasMetadata) {
        final HashSet<Integer> issuesWithNewComments = updateIssueCommentCounts(hasMetadata);

        // Set the cell factory every time - this forces the list view to update
        listView.setCellFactory(list ->
                new ListPanelCell(model, ListPanel.this, panelIndex, issuesWithNewComments));
        listView.saveSelection();

        // Supposedly this also causes the list view to update - not sure
        // if it actually does on platforms other than Linux...
        listView.setItems(null);
        listView.setItems(getIssueList());
        issueCount = getIssueList().size();

        listView.restoreSelection();
        this.setId(model.getDefaultRepo() + "_col" + panelIndex);
    }

    private void setupListView() {
        setVgrow(listView, Priority.ALWAYS);
        setupKeyboardShortcuts();
        setupContextMenu();

        listView.setOnItemSelected(i -> {
            TurboIssue issue = listView.getItems().get(i);
            ui.triggerEvent(
                    new IssueSelectedEvent(issue.getRepoId(), issue.getId(), panelIndex, issue.isPullRequest())
            );

            // Save the stored comment count as its own comment count.
            // The refreshItems(false) call that follows will remove the highlighted effect of the comment bubble.
            // (if it was there before)
            issueCommentCounts.put(issue.getId(), issue.getCommentCount());
            issueNonSelfCommentCounts.put(issue.getId(), issue.getMetadata().getNonSelfCommentCount());
            // We assume we already have metadata, so we pass true to avoid refreshItems from trying to get
            // metadata after clicking.
            refreshItems(true);
        });
    }

    private void setupKeyboardShortcuts() {
        filterTextField.addEventHandler(KeyEvent.KEY_RELEASED, event -> {
            if (KeyboardShortcuts.BOX_TO_LIST.match(event)) {
                event.consume();
                listView.selectFirstItem();
            }
            if (event.getCode() == KeyboardShortcuts.DOUBLE_PRESS) {
                event.consume();
            }
            if (KeyPress.isDoublePress(KeyboardShortcuts.DOUBLE_PRESS, event.getCode())) {
                event.consume();
                listView.selectFirstItem();
            }
            if (KeyboardShortcuts.MAXIMIZE_WINDOW.match(event)) {
                ui.maximizeWindow();
            }
            if (KeyboardShortcuts.MINIMIZE_WINDOW.match(event)) {
                ui.minimizeWindow();
            }
            if (KeyboardShortcuts.DEFAULT_SIZE_WINDOW.match(event)) {
                ui.setDefaultWidth();
            }
            if (KeyboardShortcuts.SWITCH_DEFAULT_REPO.match(event)) {
                ui.switchDefaultRepo();
            }
        });

        addEventHandler(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() == KeyboardShortcuts.markAsRead) {
                markAsRead();
            }
            if (event.getCode() == KeyboardShortcuts.markAsUnread) {
                markAsUnread();
            }
            if (event.getCode() == KeyboardShortcuts.SHOW_DOCS) {
                ui.getBrowserComponent().showDocs();
            }
            if (KeyboardShortcuts.LIST_TO_BOX.match(event)) {
                setFocusToFilterBox();
            }
            if (event.getCode() == KeyboardShortcuts.DOUBLE_PRESS
                && KeyPress.isDoublePress(KeyboardShortcuts.DOUBLE_PRESS, event.getCode())) {

                setFocusToFilterBox();
            }
            if (event.getCode() == KeyboardShortcuts.SHOW_ISSUES) {
                if (KeyPress.isValidKeyCombination(KeyboardShortcuts.GOTO_MODIFIER, event.getCode())) {
                    ui.getBrowserComponent().showIssues();
                }
            }
            if (event.getCode() == KeyboardShortcuts.SHOW_PULL_REQUESTS) {
                if (KeyPress.isValidKeyCombination(KeyboardShortcuts.GOTO_MODIFIER, event.getCode())) {
                    ui.getBrowserComponent().showPullRequests();
                }
            }
            if (event.getCode() == KeyboardShortcuts.SHOW_HELP) {
                if (KeyPress.isValidKeyCombination(KeyboardShortcuts.GOTO_MODIFIER, event.getCode())) {
                    ui.getBrowserComponent().showDocs();
                }
            }
            if (event.getCode() == KeyboardShortcuts.SHOW_KEYBOARD_SHORTCUTS) {
                if (KeyPress.isValidKeyCombination(KeyboardShortcuts.GOTO_MODIFIER, event.getCode())) {
                    ui.getBrowserComponent().showKeyboardShortcuts();
                }
            }
            if (event.getCode() == KeyboardShortcuts.SHOW_CONTRIBUTORS) {
                if (KeyPress.isValidKeyCombination(KeyboardShortcuts.GOTO_MODIFIER, event.getCode())) {
                    ui.getBrowserComponent().showContributors();
                    event.consume();
                }
            }
            if (event.getCode() == KeyboardShortcuts.scrollToTop) {
                ui.getBrowserComponent().scrollToTop();
            }
            if (event.getCode() == KeyboardShortcuts.scrollToBottom) {
                if (!KeyboardShortcuts.MINIMIZE_WINDOW.match(event)) {
                    ui.getBrowserComponent().scrollToBottom();
                }
            }
            if (event.getCode() == KeyboardShortcuts.scrollUp || event.getCode() == KeyboardShortcuts.scrollDown) {
                ui.getBrowserComponent().scrollPage(event.getCode() == KeyboardShortcuts.scrollDown);
            }
            if (event.getCode() == KeyboardShortcuts.GOTO_MODIFIER) {
                KeyPress.setLastKeyPressedCodeAndTime(event.getCode());
            }
            if (event.getCode() == KeyboardShortcuts.NEW_COMMENT && ui.getBrowserComponent().isCurrentUrlIssue()) {
                ui.getBrowserComponent().switchToConversationTab();
                ui.getBrowserComponent().jumpToComment();
            }
            if (event.getCode() == KeyboardShortcuts.SHOW_LABELS) {
                if (KeyPress.isValidKeyCombination(KeyboardShortcuts.GOTO_MODIFIER, event.getCode())) {
                    ui.getBrowserComponent().newLabel();
                } else {
                    changeLabels();
                }
            }
            if (event.getCode() == KeyboardShortcuts.MANAGE_ASSIGNEES && ui.getBrowserComponent().isCurrentUrlIssue()) {
                ui.getBrowserComponent().switchToConversationTab();
                ui.getBrowserComponent().manageAssignees(event.getCode().toString());
            }
            if (event.getCode() == KeyboardShortcuts.SHOW_MILESTONES) {
                if (KeyPress.isValidKeyCombination(KeyboardShortcuts.GOTO_MODIFIER, event.getCode())) {
                    ui.getBrowserComponent().showMilestones();
                } else if (ui.getBrowserComponent().isCurrentUrlIssue()) {
                    ui.getBrowserComponent().switchToConversationTab();
                    ui.getBrowserComponent().manageMilestones(event.getCode().toString());
                }
            }
            if (KeyboardShortcuts.MAXIMIZE_WINDOW.match(event)) {
                ui.maximizeWindow();
            }
            if (KeyboardShortcuts.MINIMIZE_WINDOW.match(event)) {
                ui.minimizeWindow();
            }
            if (KeyboardShortcuts.DEFAULT_SIZE_WINDOW.match(event)) {
                ui.setDefaultWidth();
            }
            if (KeyboardShortcuts.SWITCH_DEFAULT_REPO.match(event)) {
                ui.switchDefaultRepo();
            }
        });
    }

    private ContextMenu setupContextMenu() {
        markAsReadUnreadMenuItem.setOnAction(e -> {
            String menuItemText = markAsReadUnreadMenuItem.getText();

            if (menuItemText.equals(markAsReadMenuItemText)) {
                markAsRead();
            } else if (menuItemText.equals(markAsUnreadMenuItemText)) {
                markAsUnread();
            }
        });

        changeLabelsMenuItem.setText(changeLabelsMenuItemText);
        changeLabelsMenuItem.setOnAction(e -> {
            changeLabels();
        });

        contextMenu.getItems().addAll(markAsReadUnreadMenuItem, changeLabelsMenuItem);
        contextMenu.setOnShowing(e -> {
            updateContextMenu(contextMenu);
        });

        listView.setContextMenu(contextMenu);

        return contextMenu;
    }

    private ContextMenu updateContextMenu(ContextMenu contextMenu) {
        updateMarkAsReadUnreadMenuItem();
        updateChangeLabelsMenuItem();

        return contextMenu;
    }

    public ContextMenu getContextMenu() {
        return contextMenu;
    }

    private MenuItem updateChangeLabelsMenuItem() {
        Optional<TurboIssue> item = listView.getSelectedItem();
        if (item.isPresent()) {
            changeLabelsMenuItem.setDisable(false);
        } else {
            changeLabelsMenuItem.setDisable(true);
        }

        return changeLabelsMenuItem;
    }

    private MenuItem updateMarkAsReadUnreadMenuItem() {
        Optional<TurboIssue> item = listView.getSelectedItem();
        if (item.isPresent()) {
            markAsReadUnreadMenuItem.setDisable(false);
            TurboIssue selectedIssue = item.get();

            if (selectedIssue.isCurrentlyRead()) {
                markAsReadUnreadMenuItem.setText(markAsUnreadMenuItemText);
            } else {
                markAsReadUnreadMenuItem.setText(markAsReadMenuItemText);
            }
        } else {
            markAsReadUnreadMenuItem.setDisable(true);
        }

        return markAsReadUnreadMenuItem;
    }

    private void setFocusToFilterBox() {
        if (ui.isTestMode()) {
            ui.triggerEvent(new UIComponentFocusEvent(UIComponentFocusEvent.EventType.FILTER_BOX));
        }
        filterTextField.requestFocus();
        filterTextField.setText(filterTextField.getText().trim());
        filterTextField.positionCaret(filterTextField.getLength());

        addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyboardShortcuts.downIssue ||
                    event.getCode() == KeyboardShortcuts.upIssue) {
                listView.selectFirstItem();
            }
        });
    }

    public int getIssueCount() {
        return issueCount;
    }

    public TurboIssue getSelectedIssue() {
        return listView.getSelectedItem().get();
    }

    /* Methods that perform user's actions under the context of this ListPanel */

    private void markAsRead() {
        Optional<TurboIssue> item = listView.getSelectedItem();
        if (item.isPresent()) {
            TurboIssue issue = item.get();
            LocalDateTime now = LocalDateTime.now();
            ui.prefs.setMarkedReadAt(issue.getRepoId(), issue.getId(), now);
            issue.setMarkedReadAt(Optional.of(now));
            issue.setIsCurrentlyRead(true);
            parentPanelControl.refresh();
            listView.selectNextItem();
        }
    }

    private void markAsUnread() {
        Optional<TurboIssue> item = listView.getSelectedItem();
        if (item.isPresent()) {
            TurboIssue issue = item.get();
            ui.prefs.clearMarkedReadAt(issue.getRepoId(), issue.getId());
            issue.setMarkedReadAt(Optional.empty());
            issue.setIsCurrentlyRead(false);
            parentPanelControl.refresh();
        }
    }

    private void changeLabels() {
        ui.triggerEvent(new ShowLabelPickerEvent(getSelectedIssue()));
    }
}
