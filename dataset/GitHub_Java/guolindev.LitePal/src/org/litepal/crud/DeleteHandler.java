package org.litepal.crud;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.litepal.crud.model.AssociationsInfo;
import org.litepal.exceptions.DataSupportException;
import org.litepal.util.BaseUtility;
import org.litepal.util.Const;
import org.litepal.util.DBUtility;

import android.database.sqlite.SQLiteDatabase;

/**
 * This is a component under DataSupport. It deals with the deleting stuff as
 * primary task.
 * 
 * @author tony
 * @since 1.1
 */
public class DeleteHandler extends DataHandler {

	/**
	 * To store associated tables of current model's table. Only used while
	 * deleting by id.
	 */
	private List<String> foreignKeyTableToDelete;

	/**
	 * Initialize {@link DataHandler#mDatabase} for operating database. Do not
	 * allow to create instance of DeleteHandler out of CRUD package.
	 * 
	 * @param db
	 *            The instance of SQLiteDatabase.
	 */
	DeleteHandler(SQLiteDatabase db) {
		mDatabase = db;
	}

	/**
	 * The open interface for other classes in CRUD package to delete. Using
	 * baseObj to decide which record to delete. The baseObj must be saved
	 * already(using {@link DataSupport#isSaved()} to test), or nothing will be
	 * deleted. This method can action cascade delete. When the record is
	 * deleted from database, all the referenced data such as foreign key value
	 * will be removed too.
	 * 
	 * @param baseObj
	 *            The record to delete.
	 * @return The number of rows affected. Including cascade delete rows.
	 * @throws DataSupportException
	 */
	int onDelete(DataSupport baseObj) {
		try {
			if (baseObj.isSaved()) {
				analyzeAssociations(baseObj);
				int rowsAffected = deleteCascade(baseObj);
				rowsAffected += mDatabase.delete(baseObj.getTableName(),
						"id = " + baseObj.getBaseObjId(), null);
				return rowsAffected;
			}
		} catch (Exception e) {
			throw new DataSupportException(e.getMessage());
		}
		return 0;
	}

	/**
	 * The open interface for other classes in CRUD package to delete. Using
	 * modelClass to decide which table to delete from, and id to decide a
	 * specific row. This method can action cascade delete. When the record is
	 * deleted from database, all the referenced data such as foreign key value
	 * will be removed too.
	 * 
	 * @param modelClass
	 *            Which table to delete from.
	 * @param id
	 *            Which record to delete.
	 * @return The number of rows affected. Including cascade delete rows.
	 * @throws DataSupportException
	 */
	int onDelete(Class<?> modelClass, long id) {
		try {
			analyzeAssociations(modelClass);
			int rowsAffected = deleteCascade(modelClass, id);
			rowsAffected += mDatabase.delete(getTableName(modelClass), "id = " + id, null);
			getForeignKeyTableToDelete().clear();
			return rowsAffected;
		} catch (Exception e) {
			throw new DataSupportException(e.getMessage());
		}
	}

	/**
	 * Analyze the associations of modelClass and store the associated tables.
	 * The associated tables might be used when deleting referenced data of a
	 * specified row.
	 * 
	 * @param modelClass
	 *            To get associations of this class.
	 */
	private void analyzeAssociations(Class<?> modelClass) {
		Collection<AssociationsInfo> associationInfos = getAssociationInfo(modelClass.getName());
		for (AssociationsInfo associationInfo : associationInfos) {
			String associatedTableName = DBUtility.getTableNameByClassName(associationInfo
					.getAssociatedClassName());
			if (associationInfo.getAssociationType() == Const.Model.MANY_TO_ONE
					|| associationInfo.getAssociationType() == Const.Model.ONE_TO_ONE) {
				String classHoldsForeignKey = associationInfo.getClassHoldsForeignKey();
				if (!modelClass.getName().equals(classHoldsForeignKey)) {
					getForeignKeyTableToDelete().add(associatedTableName);
				}
			} else if (associationInfo.getAssociationType() == Const.Model.MANY_TO_MANY) {
				String joinTableName = DBUtility.getIntermediateTableName(getTableName(modelClass),
						associatedTableName);
				joinTableName = BaseUtility.changeCase(joinTableName);
				getForeignKeyTableToDelete().add(joinTableName);
			}
		}
	}

	/**
	 * Use the analyzed result of associations to delete referenced data. So
	 * this method must be called after {@link #analyzeAssociations(Class)}.
	 * There're two parts of referenced data to delete. The foreign key rows in
	 * associated table and the foreign key rows in intermediate join table.
	 * 
	 * @param modelClass
	 *            To get the table name and combine with id as a foreign key
	 *            column.
	 * @param id
	 *            Delete all the rows which referenced with this id.
	 * @return The number of rows affected in associated tables and intermediate
	 *         join tables.
	 */
	private int deleteCascade(Class<?> modelClass, long id) {
		int rowsAffected = 0;
		for (String associatedTableName : getForeignKeyTableToDelete()) {
			String fkName = getForeignKeyColumnName(getTableName(modelClass));
			rowsAffected += mDatabase.delete(associatedTableName, fkName + " = " + id, null);
		}
		return rowsAffected;
	}

	/**
	 * Analyze the associations of baseObj and store the result in it. The
	 * associations will be used when deleting referenced data of baseObj.
	 * 
	 * @param baseObj
	 *            The record to delete.
	 * @throws SecurityException
	 * @throws IllegalArgumentException
	 * @throws NoSuchMethodException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	private void analyzeAssociations(DataSupport baseObj) throws SecurityException,
			IllegalArgumentException, NoSuchMethodException, IllegalAccessException,
			InvocationTargetException {
		Collection<AssociationsInfo> associationInfos = getAssociationInfo(baseObj.getClassName());
		analyzeAssociatedModels(baseObj, associationInfos);
	}

	/**
	 * Use the analyzed result of associations to delete referenced data. So
	 * this method must be called after
	 * {@link #analyzeAssociations(DataSupport)}. There're two parts of
	 * referenced data to delete. The foreign key rows in associated tables and
	 * the foreign key rows in intermediate join tables.
	 * 
	 * @param baseObj
	 *            The record to delete. Now contains associations info.
	 * @return The number of rows affected in associated table and intermediate
	 *         join table.
	 */
	private int deleteCascade(DataSupport baseObj) {
		int rowsAffected;
		rowsAffected = deleteAssociatedForeignKeyRows(baseObj);
		rowsAffected += deleteAssociatedJoinTableRows(baseObj);
		return rowsAffected;
	}

	/**
	 * Delete the referenced rows of baseObj in associated tables(Many2One and
	 * One2One conditions).
	 * 
	 * @param baseObj
	 *            The record to delete. Now contains associations info.
	 * @return The number of rows affected in all associated tables.
	 */
	private int deleteAssociatedForeignKeyRows(DataSupport baseObj) {
		int rowsAffected = 0;
		Map<String, Set<Long>> associatedModelMap = baseObj.getAssociatedModelsMapWithFK();
		for (String associatedTableName : associatedModelMap.keySet()) {
			String fkName = getForeignKeyColumnName(baseObj.getTableName());
			rowsAffected += mDatabase.delete(associatedTableName,
					fkName + " = " + baseObj.getBaseObjId(), null);
		}
		return rowsAffected;
	}

	/**
	 * Delete the referenced rows of baseObj in intermediate join
	 * tables(Many2Many condition).
	 * 
	 * @param baseObj
	 *            The record to delete. Now contains associations info.
	 * @return The number of rows affected in all intermediate join tables.
	 */
	private int deleteAssociatedJoinTableRows(DataSupport baseObj) {
		int rowsAffected = 0;
		Set<String> associatedTableNames = baseObj.getAssociatedModelsMapForJoinTable().keySet();
		for (String associatedTableName : associatedTableNames) {
			String joinTableName = DBUtility.getIntermediateTableName(baseObj.getTableName(),
					associatedTableName);
			String fkName = getForeignKeyColumnName(baseObj.getTableName());
			rowsAffected += mDatabase.delete(joinTableName,
					fkName + " = " + baseObj.getBaseObjId(), null);
		}
		return rowsAffected;
	}

	/**
	 * Get all the associated tables of current model's table. Only used while
	 * deleting by id.
	 * 
	 * @return All the associated tables of current model's table.
	 */
	private List<String> getForeignKeyTableToDelete() {
		if (foreignKeyTableToDelete == null) {
			foreignKeyTableToDelete = new ArrayList<String>();
		}
		return foreignKeyTableToDelete;
	}

}
