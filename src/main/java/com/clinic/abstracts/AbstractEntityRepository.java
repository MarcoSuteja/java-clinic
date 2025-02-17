package com.clinic.abstracts;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.clinic.ClinicConnection;
import com.clinic.Pagination;

/**
 * Abstract class for creating an entity repository. Provides basic CRUD
 * function.
 * 
 * @author Jose Ryu Leonesta <jose.leonesta@student.matanauniversity.ac.id>
 */
public abstract class AbstractEntityRepository<T extends AbstractEntity> extends ClinicConnection {
    public Class<T> entityClass;

    protected AbstractEntityRepository(Class<T> entityClass) {
        this.entityClass = entityClass;
    }
    /**
     * Get an entity by id
     * @param id the id of the entity
     */
    public T get(Integer id) throws SQLException {
        ResultSet queryResult = query("SELECT * FROM " + tableName()
            + " WHERE id=" + id + ";");

        if (!queryResult.next()) return null;
        return mapEntity(queryResult);
    }

    /**
     * Delete a record in the database
     * @param id
     * @return
     */
    public Boolean delete(Integer id) throws SQLException {
        return execute("DELETE FROM " + tableName() + " WHERE id=" + id + ";");
    }

    /**
     * Get list of entity records with pagination along with the where clause
     * query example: "WHERE tableName().foreign_id=1"
     * @param pagination
     * @return <code>List<T></code> with T as the entity type
     */
    public List<T> get(Pagination pagination, String whereClause) throws SQLException {
        ResultSet countResult = query("SELECT count(id) as number FROM "
                + tableName() + ";");
        countResult.next();
        pagination.setTotalRecords(countResult.getInt(1));

        String fetchQuery = "SELECT * FROM " + tableName();
        fetchQuery += " " + whereClause + " ";
        if (pagination.getSortBy() != null
                && pagination.getSortOrder() != null) {
            fetchQuery += " ORDER BY " + normalizeFieldName(pagination.getSortBy())
                    + " " + pagination.getSortOrder();
        }

        int recordsPerPage = pagination.getRecordsPerPage() != 0
                ? pagination.getRecordsPerPage()
                : 10;

        int skip = (pagination.getPageNumber() != 0 ? pagination.getPageNumber() - 1 : 0)
                * recordsPerPage;
        fetchQuery += " LIMIT " + skip + "," + recordsPerPage + ";";

        ResultSet queryResult = query(fetchQuery);
        List<T> entities = new ArrayList<>();
        while (queryResult.next()) {
            entities.add(mapEntity(queryResult));
        }

        return entities;
    }

    /**
     * Join entity with another entity using repository
     * the fields to join the two entities. Example: a.field_id = b.id
     * @param childRepo
     * @param foreignKeyInParent
     * @param primaryKeyInChild
     * @return
     * @throws SQLException
     * @author Veronica Yose Ardilla
     */
    public List<T> join(AbstractEntityRepository<?> childRepo, String foreignKeyInParent, String primaryKeyInChild) {
        try {
            ResultSet queryResult = query("SELECT * FROM " + tableName() + " a JOIN " + childRepo.tableName() + 
                                " b ON a." + foreignKeyInParent + " = b." + primaryKeyInChild + ";");
            List<T> entities = new ArrayList<>();
            Method method;
            while(queryResult.next()){
                T resultEntity = mapEntity(queryResult, "a");
                String childSetterName = "set" + childRepo.entityClass.getSimpleName();
                method = resultEntity.getClass().getMethod(childSetterName, childRepo.entityClass);
                method.invoke(resultEntity, childRepo.mapEntity(queryResult, "b"));
                entities.add(resultEntity);
            }
            return entities;
        } catch (Exception e) {
            System.out.println("Exception found in AbstractEntityRepository.join(): " + e.toString());
        }

        return null;
    }

    public List<T> join(AbstractEntityRepository<?> childRepo, String foreignKeyInParent) throws SQLException {
        return join(childRepo, foreignKeyInParent, "id");
    }

    /**
     * Search with input as "word"
     * @param pagination
     * @param word
     * @return
     * @throws SQLException
     * @author Sabrina Yose Amelia
     */
    public List<T> search(Pagination pagination, String word) throws SQLException {
        String query = " WHERE ";
        try {
            T entity = entityClass.getConstructor().newInstance();

            for (String field : entity.getTableFieldNames()) {
                query += field + " LIKE " + " '%" + word + "%' OR ";
            }
            query = query.substring(0, query.length()-3);

            return get(pagination, query);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("Exception found in AbstractEntityRepository.search(): " + e.toString());
        }
        return null;
    }

    /**
     * Get list of entity with pagination
     */
    public List<T> get(Pagination pagination) throws SQLException {
        return get(pagination, "");
    }

    /**
     * Edit the entity in the database.
     * @param entity
     * @return <code>boolean</code> representing successfully edited or not
     * @throws SQLException
     */
    public Boolean edit(T entity) throws SQLException {
        String query = "UPDATE " + tableName() + " SET ";
        try {
            for (Map.Entry<String, Method> field : getEntityAttributesInSnakeCase().entrySet()) {
                if (entity.getTableFieldNames() == null || entity.getTableFieldNames().contains(field.getKey()))
                    query += (field.getKey() + "='" + field.getValue().invoke(entity)
                            + "', ");
            }
            query = query.replaceFirst(",\\s$", " WHERE id=" + entity.getId()
                    + ";");

            return execute(query);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("Exception found in AbstractEntityRepository.edit(): " + e.toString());
        }
        return false;
    }

    /**
     * Inserts an entity record to the database
     * @param entity
     * @return <code>Integer</code> representing the generated key
     * @throws SQLException
     */
    public Integer create(T entity) throws SQLException {
        String query = "INSERT INTO " + tableName();

        try {
            query += " (";
            for (Map.Entry<String, Method> field : getEntityAttributesInSnakeCase().entrySet()) {
                if (entity.getTableFieldNames() == null || entity.getTableFieldNames().contains(field.getKey()))
                    query += "`" + (field.getKey() + "`, ");
            }
            query = query.replaceFirst(",\\s$", ") VALUES (");
            for (Map.Entry<String, Method> field : getEntityAttributesInSnakeCase().entrySet()) {
                if (entity.getTableFieldNames() == null || entity.getTableFieldNames().contains(field.getKey()))
                    query += "'" + (field.getValue().invoke(entity) + "', ");
            }
            query = query.replaceFirst(",\\s$", ");");

            return executeInsert(query);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("Exception found in AbstractEntityRepository.edit(): " + e.toString());
        }
        return 0;
    }

    /**
     * Maps a <code>ResultSet</code> into a single entity
     * @param queryResult the result of getting an entity
     * @param alias the alias of the entity int the query
     */
    public T mapEntity(ResultSet queryResult, String alias) {
        try {
            T resultEntity = entityClass.getConstructor(Integer.class).newInstance(
                    queryResult.getInt("id"));

            for (Method method : getEntityAttributeSetters()) {
                String fieldName = normalizeFieldName(method.getName().substring(3));
                if (resultEntity.getTableFieldNames() != null && !resultEntity.getTableFieldNames().contains(fieldName))
                    continue;

                fieldName = (alias.length() != 0 ? alias + "." : "") + normalizeFieldName(fieldName);

                if (method.getParameterTypes()[0] == Integer.class)
                    method.invoke(resultEntity, queryResult.getInt(fieldName));
                else if (method.getParameterTypes()[0] == String.class)
                    method.invoke(resultEntity, queryResult.getString(fieldName));
                else if (method.getParameterTypes()[0] == BigDecimal.class)
                    method.invoke(resultEntity, queryResult.getBigDecimal(fieldName));
                else if (method.getParameterTypes()[0] == Date.class)
                    method.invoke(resultEntity, queryResult.getDate(fieldName));
                else if (method.getParameterTypes()[0] == Timestamp.class)
                    method.invoke(resultEntity, queryResult.getTimestamp(fieldName));
                else if (method.getParameterTypes()[0] == LocalDate.class)
                    method.invoke(resultEntity, queryResult.getDate(fieldName).toLocalDate());
                else if (method.getParameterTypes()[0] == LocalDateTime.class)
                    method.invoke(resultEntity, queryResult.getTimestamp(fieldName).toLocalDateTime());
            }
            return resultEntity;
        } catch (Exception e) {
            System.out.println("Exception found in AbstractEntityRepository.mapEntity(): " + e.toString());
        }

        return null;
    };

    public T mapEntity(ResultSet queryResult) {
        return mapEntity(queryResult, "");
    }

    /**
     * Gets a <code>Map</code> with snake cased database's field names as key 
     * and the getter method for the entity class as value
     * @param theClass
     * @return <code>Map<String, Method></code>
     */
    private Map<String, Method> getEntityAttributesInSnakeCase() {
        Map<String, Method> result = new HashMap<String, Method>();
        for (Method method : getEntityAttributeGetters()) {
            result.put(
                normalizeFieldName(method.getName().substring(3)),
                method
            );
        }
        return result;
    }

    /**
     * Gets a list of getter method that publicly declared in the 
     * <code>entityClass</code> (id getter not included)
     * @return <code>List</code> of <code>Method</code>
     */
    public List<Method> getEntityAttributeGetters() {
        List<Method> result = new ArrayList<>();
        String excludedGetters = "getId|getTableFieldNames|getClass";
        for (Method method : entityClass.getMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isPublic(modifiers)
                && method.getName().matches("get\\D+")
                && !method.getName().matches(excludedGetters)
            ) {
                result.add(method);
            }
        }
        return result;
    }

    /**
     * Gets a list of setter method that publicly declared in the 
     * <code>entityClass</code> (id getter not included)
     * @return <code>List</code> of <code>Method</code>
     */
    public List<Method> getEntityAttributeSetters() {
        List<Method> result = new ArrayList<>();
        for (Method method : entityClass.getMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isPublic(modifiers)
                && method.getName().matches("set\\D+")
            ) {
                result.add(method);
            }
        }
        return result;
    }

    /**
     * Normalize a database field name from camelCase to snake_case.
     * Example: <code>dosageFormCategory</code> -> 
     * <code>dosage_form_category</code>
     * @param camelCaseField the field
     */
    public static String normalizeFieldName(String camelCaseField) {
        return camelCaseField.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Get the name of the table in the database
     */
    protected abstract String tableName();
}