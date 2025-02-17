package com.clinic.abstracts;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.clinic.Pagination;
import com.clinic.factories.EntityRepositoryFactory;
import com.clinic.interfaces.ICopyable;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXPagination;
import io.github.palexdev.materialfx.controls.MFXTableView;
import io.github.palexdev.materialfx.controls.MFXTableColumn;
import io.github.palexdev.materialfx.controls.cell.MFXTableRowCell;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * A GUI controller to do CRUD operation for an entity.<br>
 * The entity should extend <code>AbstractEntity</code> and implements 
 * <code>Copyable</code> interface. The <code>copy()<code> method will be used
 * when creating entity's form.
 * The entity should have corresponding repository for this controller to do
 * CRUD operation into database
 * 
 * @author Jose Ryu Leonesta <jose.leonesta@student.matanauniversity.ac.id>
 */
public abstract class AbstractCrudController<T extends AbstractEntity & ICopyable<T>, S extends AbstractEntityRepository<T>> {
    public final static int CREATE_ACTION = 1, UPDATE_ACTION = 2, DELETE_ACTION = 3;
    public MFXTableView<T> entityTable;
    public MFXPagination pagination;
    public Pagination page;
    public MFXButton createButton;
    public MFXButton updateButton;
    public MFXButton deleteButton;
    public MFXButton refreshButton;

    private Class<T> entityClass;
    private GridPane formGrid;
    private Scene formScene;
    private Scene mainScene;
    private ObjectProperty<T> selectedItemProperty;
    private T pickResult;
    private String currentFetchWhereClause;

    protected S repo;
    protected List<AbstractCrudController<?, ?>> childControllers;

    protected AbstractCrudController(Class<T> entityClass, Class<S> repoClass, String sceneTitle) {
        this.entityClass = entityClass;
        this.repo = EntityRepositoryFactory.getRepository(repoClass);
        this.selectedItemProperty = new SimpleObjectProperty<>();
        this.childControllers = new ArrayList<>();
        this.currentFetchWhereClause = "";
        this.entityTable = new MFXTableView<>();
        this.page = new Pagination();
        this.pagination = new MFXPagination();
        initTableViewSchema();
        entityTable.getSelectionModel().setAllowsMultipleSelection(true);
        bindTableToSingleSelectedItemProperty(entityTable, selectedItemProperty);
        bindPagination(page, pagination, entityTable);
        initMainScene(sceneTitle);
        initFormGrid();
        formScene = new Scene(formGrid);
    }

    protected AbstractCrudController(Class<T> entityClass, Class<S> repoClass) {
        this(entityClass, repoClass, entityClass.getSimpleName());
    }

    /**
     * Binds selection model of a table to an <code>ObjectProperty</code>
     * @param table the table
     * @param entity the entity that should have the selected value
     */
    private void bindTableToSingleSelectedItemProperty(MFXTableView<T> table, ObjectProperty<T> entity) {
        table.getSelectionModel()
                .selectionProperty()
                .addListener((MapChangeListener<? super Integer, ? super T>) change -> {
                    entity.setValue(change.getValueAdded());
                });
    }

    /**
     * Binds the clinic's pagination with the MFXPagination and set listener to 
     * fetch entities
     * @param page the clinic pagination
     * @param pagination the MFXPagination component
     * @param table the table which the pagination applies to
     */
    private void bindPagination(Pagination page, MFXPagination pagination, MFXTableView<T> table) {
        page.pageNumberProperty().bindBidirectional(pagination.currentPageProperty());
        page.totalRecordsProperty().addListener((obs, oldValue, newValue) -> {
            int maxPage = (int)newValue % page.getRecordsPerPage() == 0
                ? (int)newValue / page.getRecordsPerPage()
                : (int) newValue / page.getRecordsPerPage() + 1;
            pagination.setMaxPage(maxPage);
        });
        page.pageNumberProperty().addListener((change) -> {
            fetchEntitiesToTable(table);
        });
    }

    /**
     * Set the form fields in the grid for creating and updating entities.<br>
     * This method is meant to bind form fields to an <code>entity</code>
     * provided by the method.<br>
     * IMPORTANT: form SHOULD contain submit button generated by the method
     * <code>generateSubmitButton()</code>
     * @param formGrid the form grid for form fields to be placed
     * @param entity the entity that should be binded to the form fields
     */
    protected abstract void setFormGrid(GridPane formGrid, T entity);

    /**
     * Set the current fetching where clause for controller to query
     * @param whereClause
     */
    public void setCurrentFetchWhereClause(String whereClause) {
        this.currentFetchWhereClause = whereClause;
    }

    /**
     * Fetch entity data and set it into the table view.
     * @param whereClause the where clause on the query to perform, example: "WHERE foreign_id=1"
     * @param entityTable the table which data should went to
     */
    public void fetchEntitiesToTable(MFXTableView<T> entityTable, String whereClause) {
        ObservableList<T> entities;
        try {
            entities = FXCollections.observableArrayList(repo.get(page, whereClause));
            entityTable.setItems(entities);
            entityTable.autosize();
        } catch (SQLException e) {
            System.out.println("Exception caught in AbstractController.fetchEntitiesToTable(): " + e.toString());
        }
    }

    /**
     * Fetch entity data and set it into the table view.
     * @param entityTable the table which data should went to
     */
    public void fetchEntitiesToTable(MFXTableView<T> entityTable) {
        fetchEntitiesToTable(entityTable, "");
    }

    /**
     * Fetch entity data and set it into the table view.
     */
    public void fetchEntitiesToTable() {
        fetchEntitiesToTable(entityTable, currentFetchWhereClause);
    }

    /**
     * Show a table and a pick button to pick an entity from the table
     * @return the selected entity
     */
    public T pickEntity() {
        ObjectProperty<T> selectedItemProperty = new SimpleObjectProperty<>();
        VBox pickLayout = new VBox();
        pickLayout.setAlignment(Pos.TOP_LEFT);
        pickLayout.setSpacing(10.0);
        pickLayout.setPadding(new Insets(20));

        MFXButton pickButton = new MFXButton("Pick");
        pickButton.disableProperty().bind(selectedItemProperty.isNull());

        MFXTableView<T> pickTable = new MFXTableView<>();

        initTableViewSchema(pickTable);
        fetchEntitiesToTable(pickTable);
        bindTableToSingleSelectedItemProperty(pickTable, selectedItemProperty);
        pickLayout.getChildren().addAll(
                pickButton,
                pickTable);
        Scene pickScene = new Scene(pickLayout);
        Stage pickStage = new Stage();
        pickButton.setOnAction((event) -> {
            T selectedItem = selectedItemProperty.get();
            pickResult = getNewEntityInstance(selectedItem.getId()).copy(selectedItem);
            pickStage.close();
        });
        pickStage.setTitle("Pick " + entityClass.getSimpleName());
        pickStage.setScene(pickScene);
        pickStage.showAndWait();
        return pickResult;
    }

    /**
     * Get the main CRUD scene of the controller. <br>
     * WARNING: entity data is not fetched with this method, so you should
     * explicitly call <code>fetchEntitiesToTable()</code>
     * @return scene containing table view and button actions
     */
    public Scene getMainScene() {
        return mainScene;
    }

    /**
     * Show a stage to create an entity
     */
    public void showCreateForm() {
        showForm(CREATE_ACTION);
    }

    /**
     * Show a stage to update an entity
     */
    public void showUpdateForm() {
        showForm(UPDATE_ACTION);
    }

    /**
     * Show a confirmation dialog to delete an entity
     */
    public void showDeleteForm() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Delete data?");
        confirmation.showAndWait();
        if (confirmation.getResult() == ButtonType.OK) {
            actEntity(selectedItemProperty.get(), DELETE_ACTION);
            fetchEntitiesToTable();
        }
    }

    /**
     * Initializes new grid object and show a stage to do entity creation
     * or update.
     * @param action <code>CREATE_ACTION</code> or <code>UPDATE_ACTION</code>
     */
    private void showForm(int action) {
        initFormGrid();
        T entity = action == CREATE_ACTION
                ? getNewEntityInstance(null)
                : getCopyOfSelectedItem();
        setFormGrid(formGrid, entity);
        if (!childControllers.isEmpty())
            for (AbstractCrudController<?, ?> controller : childControllers) {
                if (entity.getId() != null)
                    controller.setCurrentFetchWhereClause("WHERE " +
                            AbstractEntityRepository
                                    .normalizeFieldName(entityClass.getSimpleName())
                            + "_id=" + entity.getId());
                controller.fetchEntitiesToTable();
            }
        formScene.setRoot(formGrid);
        Stage formStage = new Stage();
        formStage.setScene(formScene);
        formStage.setTitle(action == CREATE_ACTION ? "Create "
                : "Update " +
                        entityClass.getSimpleName());
        formStage.showAndWait();
    }

    /**
     * Generates a button that handle form submission
     * @param text text to be displayed on the button
     * @param entity the entity that should be created or updated
     * @return <code>MFXButton</code> that has handler that handles form submission
     */
    protected MFXButton generateSubmitButton(String text, T entity) {
        MFXButton submitButton = new MFXButton();
        submitButton.setText(text);
        submitButton.setOnAction((event) -> {
            int action = entity.getId() != 0 ? UPDATE_ACTION : CREATE_ACTION;
            actEntity(entity, action);
            Stage stage = (Stage) submitButton.getScene().getWindow();
            stage.close();
            fetchEntitiesToTable();
        });
        return submitButton;
    }

    /**
     * Do database operation on an entity
     * @param entity entity to be acted upon
     * @param action <code>CREATE_ACTION</code> or <code>UPDATE_ACTION</code> or <code>DELETE_ACTION</code>
     */
    protected void actEntity(T entity, int action) {
        try {
            if (action == CREATE_ACTION)
                repo.create(entity);
            else if (action == UPDATE_ACTION)
                repo.edit(entity);
            else if (action == DELETE_ACTION)
                repo.delete(entity.getId());
            else
                System.out.println("AbstractController.actEntity(): Invalid action");
        } catch (SQLException e) {
            System.out.println("Exception caught in AbstractController.actEntity(): " + e.toString());
        }
    }

    /**
     * Initialize main scene which configures button and adds them along with
     * table view.
     * @param sceneTitle the title that should show in the scene
     */
    private void initMainScene(String sceneTitle) {
        createButton = new MFXButton("Create");
        updateButton = new MFXButton("Update");
        deleteButton = new MFXButton("Delete");
        refreshButton = new MFXButton("Refresh");
        createButton.setOnAction(event -> showCreateForm());
        updateButton.setOnAction(event -> showUpdateForm());
        deleteButton.setOnAction(event -> showDeleteForm());
        refreshButton.setOnAction(event -> fetchEntitiesToTable());

        updateButton.disableProperty().bind(selectedItemProperty.isNull());
        deleteButton.disableProperty().bind(selectedItemProperty.isNull());

        HBox buttonLayout = new HBox();
        buttonLayout.setSpacing(5.0);
        buttonLayout.getChildren().addAll(createButton, updateButton, deleteButton, refreshButton);

        VBox sceneLayout = new VBox();
        sceneLayout.setAlignment(Pos.BASELINE_LEFT);
        sceneLayout.setSpacing(10.0);
        sceneLayout.setPadding(new Insets(20));
        entityTable.setPrefHeight(425);
        entityTable.setPrefWidth(700);
        entityTable.autosize();
        Label label = new Label(sceneTitle);
        label.setMaxWidth(Double.MAX_VALUE);
        AnchorPane.setLeftAnchor(label, 0.0);
        AnchorPane.setRightAnchor(label, 0.0);
        label.setAlignment(Pos.CENTER);
        label.setStyle("-fx-font-weight: bold");
        sceneLayout.getChildren().addAll(
                label,
                buttonLayout,
                entityTable,
                pagination);
        mainScene = new Scene(sceneLayout);
    }

    /**
     * Initialize a <code>MFXTableView</code> columns
     * @param entityTable the table to be initialized
     */
    protected abstract void initTableViewSchema(MFXTableView<T> entityTable);

    /**
     * Init the <code>AbstractCrudController.entityTable</code>
     */
    protected void initTableViewSchema() {
        initTableViewSchema(entityTable);
    }

    /**
     * Add a column to the current table using property of entity
     * @param columnLabel the label to display in the table heading
     * @param tableColumnKey the column key for the <code>PropertyValueFactory</code>
     * @param prefWidth the prefWidth of the table column
     */
    protected void addTableColumn(MFXTableView<T> entityTable, String columnLabel, Function<T, Serializable> extractor) {
        MFXTableColumn<T> tableColumn = new MFXTableColumn<>(columnLabel);
        tableColumn.setRowCellFactory(entity -> new MFXTableRowCell<>(extractor));
        tableColumn.setColumnResizable(true);
        entityTable.getTableColumns().add(tableColumn);
    }

    protected <C> void addTableColumn(MFXTableView<T> entityTable, String columnLabel, Function<T, C> childExtractor, Function<C, Serializable> extractor) {
        MFXTableColumn<T> tableColumn = new MFXTableColumn<>(columnLabel);
        tableColumn.setRowCellFactory(entity -> new MFXTableRowCell<>(childExtractor.andThen((t) -> extractor.apply(t))));
        tableColumn.setColumnResizable(true);
        entityTable.getTableColumns().add(tableColumn);
    }

    /**
     * Get copy of selected item in the table.
     * @return new identical entity object with the selected item.
     */
    private T getCopyOfSelectedItem() {
        T selectedItem = selectedItemProperty.get();
        try {
            return entityClass
                    .getConstructor(Integer.class)
                    .newInstance(selectedItem.getId())
                    .copy(selectedItem);
        } catch (Exception e) {
            System.out.println("Exception caught in AbstractCrudController.getCopyOfSelectedItem(): " + e.toString());
        }
        return null;
    }

    /**
     * Generates new entity instance
     * @param id id of entity
     */
    private T getNewEntityInstance(Integer id) {
        try {
            return entityClass.getConstructor(Integer.class).newInstance(id);
        } catch (Exception e) {
            System.out.println("Exception caught in AbstractCrudController.getNewEntityInstance(): " + e.toString());
        }
        return null;
    }

    /**
     * Set form grid alignments and spaces.
     */
    private void initFormGrid() {
        formGrid = new GridPane();
        formGrid.setAlignment(Pos.TOP_LEFT);
        formGrid.setHgap(10);
        formGrid.setVgap(10);
        formGrid.setPrefWidth(500);
        formGrid.setPadding(new Insets(25));
    }

    /**
     * Get class of the entity for picking entity purpose
     */
    public Class<T> getEntityClass() {
        return entityClass;
    }

    /**
     * Get repository of the entity
     */
    public S getRepo() {
        return repo;
    }
}
