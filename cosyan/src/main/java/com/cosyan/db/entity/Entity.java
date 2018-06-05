package com.cosyan.db.entity;

import org.json.JSONArray;
import org.json.JSONObject;

import com.cosyan.db.lang.transaction.Result;
import com.cosyan.db.meta.MaterializedTable;
import com.cosyan.db.model.BasicColumn;
import com.google.common.collect.ImmutableList;

import lombok.Data;

public class Entity extends Result {

  @Data
  public static class Field {
    private final String name;
    private final String type;
    private final Object value;
  }

  @Data
  public static class ForeignKey {
    private final String columnName;
    private final String type;
    private final String fkName;
    private final String refTable;
    private final Object value;
  }

  @Data
  public static class ReverseForeignKey {
    private final String name;
    private final String refTable;
    private final String refColumn;
  }

  private final String entityType;
  private final String pkColumn;
  private final ImmutableList<Field> fields;
  private ImmutableList<ForeignKey> foreignKeys;
  private ImmutableList<ReverseForeignKey> reverseForeignKeys;

  public Entity(MaterializedTable tableMeta, ImmutableList<BasicColumn> header, Object[] values) {
    super(true);
    this.entityType = tableMeta.tableName();
    this.pkColumn = tableMeta.pkColumn().get().getName();
    ImmutableList.Builder<Field> fields = ImmutableList.builder();
    ImmutableList.Builder<ForeignKey> foreignKeys = ImmutableList.builder();
    for (int i = 0; i < header.size(); i++) {
      BasicColumn column = header.get(i);
      if (!tableMeta.isColumnForeignKey(column.getName())) {
        fields.add(new Field(column.getName(), column.getType().getName(), values[i]));
      } else {
        com.cosyan.db.model.Keys.ForeignKey foreignKey = tableMeta.getColumnForeignKey(column.getName());
        foreignKeys.add(new ForeignKey(
            column.getName(),
            column.getType().getName(),
            foreignKey.getName(),
            foreignKey.getRefTable().tableName(),
            values[i]));
      }
    }
    ImmutableList.Builder<ReverseForeignKey> reverseForeignKeys = ImmutableList.builder();
    for (com.cosyan.db.model.Keys.ReverseForeignKey rfk : tableMeta.reverseForeignKeys().values()) {
      reverseForeignKeys.add(new ReverseForeignKey(
          rfk.getName(), rfk.getRefTable().tableName(), rfk.getRefColumn().getName()));
    }
    this.fields = fields.build();
    this.foreignKeys = foreignKeys.build();
    this.reverseForeignKeys = reverseForeignKeys.build();
  }

  @Override
  public JSONObject toJSON() {
    JSONObject obj = new JSONObject();
    obj.put("type", entityType);
    obj.put("pk", pkColumn);
    JSONArray fieldList = new JSONArray();
    for (Field field : fields) {
      JSONObject fieldObj = new JSONObject();
      fieldObj.put("name", field.getName());
      fieldObj.put("type", field.getType());
      fieldObj.put("value", QueryResult.prettyPrint(field.getValue()));
      fieldList.put(fieldObj);
    }
    obj.put("fields", fieldList);
    JSONArray fkList = new JSONArray();
    for (ForeignKey foreignKey : foreignKeys) {
      JSONObject fkObj = new JSONObject();
      fkObj.put("columnName", foreignKey.getColumnName());
      fkObj.put("type", foreignKey.getType());
      fkObj.put("name", foreignKey.getFkName());
      fkObj.put("refTable", foreignKey.getRefTable());
      fkObj.put("value", foreignKey.getValue());
      fkList.put(fkObj);
    }
    obj.put("foreignKeys", fkList);
    JSONArray rfkList = new JSONArray();
    for (ReverseForeignKey reverseForeignKey : reverseForeignKeys) {
      JSONObject rfkObj = new JSONObject();
      rfkObj.put("name", reverseForeignKey.getName());
      rfkObj.put("refTable", reverseForeignKey.getRefTable());
      rfkObj.put("refColumn", reverseForeignKey.getRefColumn());
      rfkList.put(rfkObj);
    }
    obj.put("reverseForeignKeys", rfkList);
    return obj;
  }
}
