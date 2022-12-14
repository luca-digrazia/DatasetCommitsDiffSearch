package org.litepal.crud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.litepal.exceptions.DataSupportException;
import org.litepal.tablemanager.Connector;
import org.litepal.util.BaseUtility;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/**
 * 
 * @author Tony Green
 * @since 1.1
 */
public abstract class DataSupport {

	/**
	 * The identify of each model. LitePal will generate the value
	 * automatically. Do not try to assign or modify it.
	 */
	private long baseObjId;

	/**
	 * A map contains all the associated models' id with M2O or O2O
	 * associations. Each corresponding table of these models contains a foreign
	 * key column.
	 */
	private Map<String, Set<Long>> associatedModelsMapWithFK;

	/**
	 * A map contains all the associated models' id with M2O or O2O association.
	 * Each corresponding table of these models doesn't contain foreign key
	 * column. Instead self model has a foreign key column in the corresponding
	 * table.
	 */
	private Map<String, Long> associatedModelsMapWithoutFK;

	/**
	 * A map contains all the associated models' id with M2M association.
	 */
	private Map<String, Set<Long>> associatedModelsMapForJoinTable;

	/**
	 * When updating a model and the associations breaks between current model
	 * and others, if current model holds a foreign key, it need to be cleared.
	 * This list holds all the foreign key names that need to clear.
	 */
	private List<String> listToClearSelfFK;

	/**
	 * When updating a model and the associations breaks between current model
	 * and others, clear all the associated models' foreign key value if it
	 * exists. This list holds all the associated table names that need to
	 * clear.
	 */
	private List<String> listToClearAssociatedFK;

	/**
	 * A list holds all the field names which need to be updated into default
	 * value of model.
	 */
	private List<String> fieldsToSetToDefault;

	/**
	 * Deletes the record in the database by id.<br>
	 * The data in other tables which is referenced with the record will be
	 * removed too.
	 * 
	 * @param modelClass
	 *            Which table to delete from by class.
	 * @param id
	 *            Which record to delete.
	 * @return The number of rows affected. Including cascade delete rows.
	 */
	public static synchronized int delete(Class<?> modelClass, long id) {
		DeleteHandler deleteHandler = new DeleteHandler(Connector.getDatabase());
		return deleteHandler.onDelete(modelClass, id);
	}

	/**
	 * Updates the corresponding record by id with ContentValues. Returns the
	 * number of affected rows.
	 * 
	 * @param modelClass
	 *            Which table to update by class.
	 * @param id
	 *            Which record to update.
	 * @param values
	 *            A map from column names to new column values. null is a valid
	 *            value that will be translated to NULL.
	 * @return The number of rows affected.
	 * 
	 * @throws DataSupportException
	 */
	public static synchronized int update(Class<?> modelClass, long id, ContentValues values) {
		UpdateHandler updateHandler = new UpdateHandler(Connector.getDatabase());
		return updateHandler.onUpdate(modelClass, id, values);
	}

	/**
	 * Updates all records with details given if they match a set of conditions
	 * supplied. This method constructs a single SQL UPDATE statement and sends
	 * it to the database.
	 * 
	 * @param modelClass
	 *            Which table to update by class.
	 * @param conditions
	 *            A string array representing the WHERE part of an SQL
	 *            statement. First parameter is the WHERE clause to apply when
	 *            updating. The way of specifying place holders is to insert one
	 *            or more question marks in the SQL. The first question mark is
	 *            replaced by the second element of the array, the next question
	 *            mark by the third, and so on. Passing null will update all
	 *            rows.
	 * @param values
	 *            A map from column names to new column values. null is a valid
	 *            value that will be translated to NULL.
	 * @return The number of rows affected.
	 * @throws DataSupportException
	 */
	public static synchronized int updateAll(Class<?> modelClass, String[] conditions,
			ContentValues values) {
		UpdateHandler updateHandler = new UpdateHandler(Connector.getDatabase());
		return updateHandler.onUpdateAll(modelClass, conditions, values);
	}

	/**
	 * Deletes the record in the database. The record must be saved already.<br>
	 * The data in other tables which is referenced with the record will be
	 * removed too.
	 * 
	 * @return The number of rows affected. Including cascade delete rows.
	 */
	public synchronized int delete() {
		int rowsAffected = 0;
		SQLiteDatabase db = Connector.getDatabase();
		db.beginTransaction();
		try {
			DeleteHandler deleteHandler = new DeleteHandler(db);
			rowsAffected = deleteHandler.onDelete(this);
			db.setTransactionSuccessful();
			return rowsAffected;
		} finally {
			db.endTransaction();
		}
	}

	/**
	 * Updates the corresponding record by id. Use setXxx to decide which
	 * columns to update. <br>
	 * <b>Note: <b> 1. If you set default value to a field, the corresponding
	 * column won't be updated. Use {@link #setToDefault(String)} to update
	 * columns into default value. 2. This method couldn't update foreign key in
	 * database. So do not use setXxx to set associations between models.<br>
	 * Returns the number of affected rows.
	 * 
	 * @param id
	 *            Which record to update.
	 * @return The number of rows affected.
	 * @throws DataSupportException
	 */
	public synchronized int update(long id) {
		UpdateHandler updateHandler = new UpdateHandler(Connector.getDatabase());
		int rowsAffected = updateHandler.onUpdate(this, id);
		getFieldsToSetToDefault().clear();
		return rowsAffected;
	}

	/**
	 * Updates all records with details given if they match a set of conditions
	 * supplied. This method constructs a single SQL UPDATE statement and sends
	 * it to the database.<br>
	 * <b>Note: <b> 1. If you set default value to a field, the corresponding
	 * column won't be updated. Use {@link #setToDefault(String)} to update
	 * columns into default value. 2. This method couldn't update foreign key in
	 * database. So do not use setXxx to set associations between models.<br>
	 * Returns the number of affected rows.
	 * 
	 * @param conditions
	 *            A string array representing the WHERE part of an SQL
	 *            statement. First parameter is the WHERE clause to apply when
	 *            updating. The way of specifying place holders is to insert one
	 *            or more question marks in the SQL. The first question mark is
	 *            replaced by the second element of the array, the next question
	 *            mark by the third, and so on. Passing null will update all
	 *            rows.
	 * @return The number of rows affected.
	 * @throws DataSupportException
	 */
	public synchronized int updateAll(String[] conditions) {
		UpdateHandler updateHandler = new UpdateHandler(Connector.getDatabase());
		int rowsAffected = updateHandler.onUpdateAll(this, conditions);
		getFieldsToSetToDefault().clear();
		return rowsAffected;
	}

	/**
	 * Saves the model. <br />
	 * <br />
	 * If the model is a new record gets created in the database, otherwise the
	 * existing record gets updated.<br />
	 * If saving process failed by any accident, the whole action will be
	 * cancelled and your database will be <b>rolled back</b>. <br />
	 * If the model has a field named id or _id and field type is int or long,
	 * the id value generated by database will assign to it after the model is
	 * saved.<br />
	 * Note that if the associated models of this model is already saved. The
	 * associations between them will be built automatically in database after
	 * it saved.
	 * 
	 * @return If the model is saved successfully, return true. Any exception
	 *         happens, return false.
	 */
	public synchronized boolean save() {
		SQLiteDatabase db = Connector.getDatabase();
		db.beginTransaction();
		try {
			SaveHandler saveHandler = new SaveHandler(db);
			saveHandler.onSave(this);
			clearAssociatedData();
			db.setTransactionSuccessful();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			db.endTransaction();
		}
	}

	/**
	 * Current model is saved or not.
	 * 
	 * @return If saved return true, or return false.
	 */
	public boolean isSaved() {
		return baseObjId > 0;
	}

	/**
	 * When updating database with {@link DataSupport#update(long)}, you must
	 * use this method to update a field into default value. Use setXxx with
	 * default value of the model won't update anything. <br>
	 * 
	 * @param fieldName
	 *            The name of field to update into default value.
	 */
	public void setToDefault(String fieldName) {
		getFieldsToSetToDefault().add(fieldName);
	}

	/**
	 * Get the baseObjId of this model if it's useful for developers. It's for
	 * system use usually. Do not try to assign or modify it.
	 * 
	 * @return The base object id.
	 */
	protected long getBaseObjId() {
		return baseObjId;
	}

	/**
	 * Get the full class name of self.
	 * 
	 * @return The full class name of self.
	 */
	protected String getClassName() {
		return getClass().getName();
	}

	/**
	 * Get the corresponding table name of current model.
	 * 
	 * @return The corresponding table name of current model.
	 */
	protected String getTableName() {
		return BaseUtility.changeCase(getClass().getSimpleName());
	}

	/**
	 * Get the list which holds all field names to update them into default
	 * value of model in database.
	 * 
	 * @return List holds all the field names which need to be updated into
	 *         default value of model.
	 */
	List<String> getFieldsToSetToDefault() {
		if (fieldsToSetToDefault == null) {
			fieldsToSetToDefault = new ArrayList<String>();
		}
		return fieldsToSetToDefault;
	}

	/**
	 * Add the id of an associated model into self model's associatedIdsWithFK
	 * map. The associated model has a foreign key column in the corresponding
	 * table.
	 * 
	 * @param associatedTableName
	 *            The table name of associated model.
	 * @param associatedId
	 *            The {@link #baseObjId} of associated model after it is saved.
	 */
	void addAssociatedModelWithFK(String associatedTableName, long associatedId) {
		Set<Long> associatedIdsWithFKSet = getAssociatedModelsMapWithFK().get(associatedTableName);
		if (associatedIdsWithFKSet == null) {
			associatedIdsWithFKSet = new HashSet<Long>();
			associatedIdsWithFKSet.add(associatedId);
			associatedModelsMapWithFK.put(associatedTableName, associatedIdsWithFKSet);
		} else {
			associatedIdsWithFKSet.add(associatedId);
		}
	}

	/**
	 * Get the associated model's map of self model. It can be used for
	 * associations actions of CRUD. The key is the name of associated model.
	 * The value is a List of id of associated models.
	 * 
	 * @return An associated model's map to update all the foreign key columns
	 *         of associated models' table with self model's id.
	 */
	Map<String, Set<Long>> getAssociatedModelsMapWithFK() {
		if (associatedModelsMapWithFK == null) {
			associatedModelsMapWithFK = new HashMap<String, Set<Long>>();
		}
		return associatedModelsMapWithFK;
	}

	/**
	 * Add the id of an associated model into self model's associatedIdsM2M map.
	 * 
	 * @param associatedModelName
	 *            The name of associated model.
	 * @param associatedId
	 *            The id of associated model.
	 */
	void addAssociatedModelForJoinTable(String associatedModelName, long associatedId) {
		Set<Long> associatedIdsM2MSet = getAssociatedModelsMapForJoinTable().get(
				associatedModelName);
		if (associatedIdsM2MSet == null) {
			associatedIdsM2MSet = new HashSet<Long>();
			associatedIdsM2MSet.add(associatedId);
			associatedModelsMapForJoinTable.put(associatedModelName, associatedIdsM2MSet);
		} else {
			associatedIdsM2MSet.add(associatedId);
		}
	}

	/**
	 * Add an empty Set into {@link #associatedModelsMapForJoinTable} with
	 * associated model name as key. Might be useful when comes to update
	 * intermediate join table.
	 * 
	 * @param associatedModelName
	 *            The name of associated model.
	 */
	void addEmptyModelForJoinTable(String associatedModelName) {
		Set<Long> associatedIdsM2MSet = getAssociatedModelsMapForJoinTable().get(
				associatedModelName);
		if (associatedIdsM2MSet == null) {
			associatedIdsM2MSet = new HashSet<Long>();
			associatedModelsMapForJoinTable.put(associatedModelName, associatedIdsM2MSet);
		}
	}

	/**
	 * Get the associated model's map for intermediate join table. It is used to
	 * save values into intermediate join table. The key is the name of
	 * associated model. The value is the id of associated model.
	 * 
	 * @return An associated model's map to save values into intermediate join
	 *         table
	 */
	Map<String, Set<Long>> getAssociatedModelsMapForJoinTable() {
		if (associatedModelsMapForJoinTable == null) {
			associatedModelsMapForJoinTable = new HashMap<String, Set<Long>>();
		}
		return associatedModelsMapForJoinTable;
	}

	/**
	 * Add the id of an associated model into self model's association
	 * collection. The associated model doesn't have a foreign key column in the
	 * corresponding table. Instead self model has a foreign key column in the
	 * corresponding table.
	 * 
	 * @param associatedTableName
	 *            The simple class name of associated model.
	 * @param associatedId
	 *            The {@link #baseObjId} of associated model after it is saved.
	 */
	void addAssociatedModelWithoutFK(String associatedTableName, long associatedId) {
		getAssociatedModelsMapWithoutFK().put(associatedTableName, associatedId);
	}

	/**
	 * Get the associated model's map of self model. It can be used for
	 * associations actions of CRUD. The key is the name of associated model.
	 * The value is the id of associated model.
	 * 
	 * @return An associated model's map to save self model with foreign key.
	 */
	Map<String, Long> getAssociatedModelsMapWithoutFK() {
		if (associatedModelsMapWithoutFK == null) {
			associatedModelsMapWithoutFK = new HashMap<String, Long>();
		}
		return associatedModelsMapWithoutFK;
	}

	/**
	 * Add a foreign key name into the clear list.
	 * 
	 * @param foreignKeyName
	 *            The name of foreign key.
	 */
	void addFKNameToClearSelf(String foreignKeyName) {
		List<String> list = getListToClearSelfFK();
		if (!list.contains(foreignKeyName)) {
			list.add(foreignKeyName);
		}
	}

	/**
	 * Get the foreign key name list to clear foreign key value in current
	 * model's table.
	 * 
	 * @return The list of foreign key names to clear in current model's table.
	 */
	List<String> getListToClearSelfFK() {
		if (listToClearSelfFK == null) {
			listToClearSelfFK = new ArrayList<String>();
		}
		return listToClearSelfFK;
	}

	/**
	 * Add an associated table name into the list to clear.
	 * 
	 * @param associatedTableName
	 *            The name of associated table.
	 */
	void addAssociatedTableNameToClearFK(String associatedTableName) {
		List<String> list = getListToClearAssociatedFK();
		if (!list.contains(associatedTableName)) {
			list.add(associatedTableName);
		}
	}

	/**
	 * Get the associated table names list which need to clear their foreign key
	 * values.
	 * 
	 * @return The list with associated table names to clear foreign key values.
	 */
	List<String> getListToClearAssociatedFK() {
		if (listToClearAssociatedFK == null) {
			listToClearAssociatedFK = new ArrayList<String>();
		}
		return listToClearAssociatedFK;
	}

	/**
	 * Clear all the data for storing associated models' data.
	 */
	private void clearAssociatedData() {
		clearIdOfModelWithFK();
		clearIdOfModelWithoutFK();
		clearIdOfModelForJoinTable();
		clearFKNameList();
	}

	/**
	 * Clear all the data in {@link #associatedModelsMapWithFK}.
	 */
	private void clearIdOfModelWithFK() {
		for (String associatedModelName : getAssociatedModelsMapWithFK().keySet()) {
			associatedModelsMapWithFK.get(associatedModelName).clear();
		}
		associatedModelsMapWithFK.clear();
	}

	/**
	 * Clear all the data in {@link #associatedModelsMapWithoutFK}.
	 */
	private void clearIdOfModelWithoutFK() {
		getAssociatedModelsMapWithoutFK().clear();
	}

	/**
	 * Clear all the data in {@link #associatedModelsMapForJoinTable}.
	 */
	private void clearIdOfModelForJoinTable() {
		for (String associatedModelName : getAssociatedModelsMapForJoinTable().keySet()) {
			associatedModelsMapForJoinTable.get(associatedModelName).clear();
		}
		associatedModelsMapForJoinTable.clear();
	}

	/**
	 * Clear all the data in {@link #listToClearSelfFK}.
	 */
	private void clearFKNameList() {
		getListToClearSelfFK().clear();
		getListToClearAssociatedFK().clear();
	}

}
