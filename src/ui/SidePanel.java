package ui;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.Model;
import model.TurboIssue;

import org.eclipse.egit.github.core.RepositoryId;

import service.ServiceManager;
import ui.issuepanel.IssueDisplayPane;
import util.DialogMessage;
import util.SessionConfigurations;

public class SidePanel extends VBox {


	public enum IssueEditMode{NIL, CREATE, EDIT};
	
	protected static final int PANEL_PREF_WIDTH = 300;
	public boolean expandedIssueView = false;

	public enum Layout {
		TABS, ISSUE, HISTORY
	}

	private Layout layout;
	private Stage parentStage;
	private Model model;
	private ColumnControl columns = null;
	IssueDisplayPane currentIssueDisplay = null;
	
	public SidePanel(Stage parentStage, Model model) {
		this.parentStage = parentStage;
		this.model = model;
		getStyleClass().add("sidepanel");
		setLayout(Layout.TABS);
	}

	// Needed due to a circular dependency with ColumnControl
	public void setColumns(ColumnControl columns) {
		this.columns = columns;
	}
	
	public Layout getLayout() {
		return layout;
	}

	private void setLayout(Layout layout) {
		this.layout = layout;
		changeLayout();
	}

	public void refresh() {
		changeLayout();
	}
	
	// For passing information to and from the issue panel display
	
	private TurboIssue displayedIssue;
	private boolean focusRequested;
	private IssueEditMode mode = IssueEditMode.NIL;
	
	public void onCreateIssueHotkey() {
		triggerIssueCreate(new TurboIssue("", "", model));
	}
	
	public void triggerIssueCreate(TurboIssue issue) {
		mode = IssueEditMode.CREATE;
		displayedIssue = issue;
		focusRequested = true;
		setLayout(Layout.ISSUE);
	}
	
	public void triggerIssueEdit(TurboIssue issue, boolean requestFocus) {
		mode = IssueEditMode.EDIT;
		displayedIssue = new TurboIssue(issue);
		focusRequested = requestFocus;
		setLayout(Layout.ISSUE);
	}
	
	public void displayTabs() {
		mode = IssueEditMode.NIL; //TODO:
		setLayout(Layout.TABS);
	}
	
	private void changeLayout() {
		getChildren().clear();
		switch (layout) {
		case TABS:
			getChildren().add(tabLayout());
			break;
		case HISTORY:
			getChildren().add(historyLayout());
			break;
		case ISSUE:
			getChildren().add(issueLayout());
			break;
		default:
			assert false;
			break;
		}
	}

	private Node tabLayout() {
		
		VBox everything = new VBox();
		
		TabPane tabs = new TabPane();
		Tab labelsTab = createLabelsTab();
		Tab milestonesTab = createMilestonesTab();
		Tab assigneesTab = createCollaboratorsTab();
		
		tabs.getTabs().addAll(labelsTab, milestonesTab, assigneesTab);
		
		HBox repoFields = createRepoFields();

		everything.getChildren().addAll(repoFields, tabs);
		
		everything.setPrefWidth(PANEL_PREF_WIDTH);
		
		return everything;
	}

	private HBox createRepoFields() {
		final ComboBox<String> comboBox = new ComboBox<String>();
		comboBox.setFocusTraversable(false);
		comboBox.setEditable(true);
		
		if (ServiceManager.getInstance().getRepoId() != null) {
			String repoId = ServiceManager.getInstance().getRepoId().generateId();
			comboBox.setValue(repoId);
			try {
				if(ServiceManager.getInstance().checkRepository(repoId)){
					comboBox.getItems().addAll(SessionConfigurations.addToLastViewedRepositories(repoId));
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				//Means checkRepository threw an exception...
				e.printStackTrace();
			}
		}
		
		comboBox.valueProperty().addListener((a, b, c) -> loadRepo(comboBox));

		HBox repoIdBox = new HBox();
		repoIdBox.setSpacing(5);
		repoIdBox.setPadding(new Insets(5));
		repoIdBox.setAlignment(Pos.CENTER);
		repoIdBox.getChildren().addAll(comboBox);
		comboBox.prefWidthProperty().bind(repoIdBox.widthProperty());
		return repoIdBox;
	}
	
	private boolean checkRepoAccess(RepositoryId repoId){
		try {
			if(!ServiceManager.getInstance().checkRepository(repoId)){
				Platform.runLater(() -> {
					DialogMessage.showWarningDialog("Error loading repository", "Repository does not exist or you do not have permission to access the repository");
				});
				return false;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return true;
	}

	private void loadRepo(final ComboBox<String> comboBox) {
		RepositoryId repoId = RepositoryId.createFromId(comboBox.getValue());
		if(!checkRepoAccess(repoId)){
			return;
		}
		if (repoId != null) {
			columns.saveSession();
			Task<HashMap<String, List>> task = new Task<HashMap<String, List>>(){
				@Override
				protected HashMap<String, List> call() throws Exception {
					return ServiceManager.getInstance().getGitHubResources(repoId);
				}
			};
			DialogMessage.showProgressDialog(task, "Loading issues from " + repoId.generateId() + "...");
			Thread thread = new Thread(task);
			thread.setDaemon(true);
			thread.start();
			
			task.setOnSucceeded(wse -> {
				HashMap<String, List> map = task.getValue();
				if (map != null) {
					StatusBar.displayMessage("Issues loaded successfully!");
					try {
						model.loadComponents(repoId, map);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					ServiceManager.getInstance().setupAndStartModelUpdate();
					columns.resumeColumns();
					SidePanel.this.refresh();
				} else {
					StatusBar.displayMessage("Issues failed to load. Please try again.");
				}
			});
			
			task.setOnFailed(wse -> {
				StatusBar.displayMessage("An error occurred: " + task.getException());
			});
		}
	}

	private Tab createCollaboratorsTab() {
		Tab tab = new Tab();
		tab.setClosable(false);
		tab.setText("Collaborators");
		tab.setContent(new CollaboratorManagementComponent(model).initialise());
		return tab;
	}

	private Tab createMilestonesTab() {
		Tab tab = new Tab();
		tab.setClosable(false);
		tab.setText("Milestones");
		tab.setContent(new MilestoneManagementComponent(parentStage, model).initialise());
		return tab;
	}

	private Tab createLabelsTab() {
		Tab tab = new Tab();
		tab.setClosable(false);
		tab.setText("Labels");
		tab.setContent(new LabelManagementComponent(parentStage, model, this).initialise());
		return tab;
	}

	private Node historyLayout() {
		return new VBox();
	}

	private Node issueLayout() {
		if(currentIssueDisplay != null){
			currentIssueDisplay.cleanup();
		}
		currentIssueDisplay = new IssueDisplayPane(displayedIssue, parentStage, model, columns, this, focusRequested, mode);
		return currentIssueDisplay;
	}
}
