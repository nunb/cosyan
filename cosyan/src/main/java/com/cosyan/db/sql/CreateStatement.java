package com.cosyan.db.sql;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.cosyan.db.lock.ResourceLock;
import com.cosyan.db.model.ColumnMeta.BasicColumn;
import com.cosyan.db.model.ColumnMeta.DerivedColumn;
import com.cosyan.db.model.DataTypes;
import com.cosyan.db.model.DataTypes.DataType;
import com.cosyan.db.model.Keys.ForeignKey;
import com.cosyan.db.model.Keys.PrimaryKey;
import com.cosyan.db.model.MetaRepo;
import com.cosyan.db.model.MetaRepo.ModelException;
import com.cosyan.db.model.TableMeta.MaterializedTableMeta;
import com.cosyan.db.sql.Result.MetaStatementResult;
import com.cosyan.db.sql.SyntaxTree.Expression;
import com.cosyan.db.sql.SyntaxTree.Ident;
import com.cosyan.db.sql.SyntaxTree.Node;
import com.cosyan.db.sql.SyntaxTree.Statement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import lombok.Data;
import lombok.EqualsAndHashCode;

public class CreateStatement {

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class Create extends Node implements Statement {
    private final String name;
    private final ImmutableList<ColumnDefinition> columns;
    private final ImmutableList<ConstraintDefinition> constraints;

    @Override
    public Result execute(MetaRepo metaRepo) throws ModelException, IOException {
      ImmutableMap.Builder<String, BasicColumn> columnsBuilder = ImmutableMap.builder();
      int i = 0;
      for (ColumnDefinition column : columns) {
        BasicColumn basicColumn = new BasicColumn(
            i++,
            column.getName(),
            column.getType(),
            column.isNullable(),
            column.isUnique());
        columnsBuilder.put(column.getName(), basicColumn);
      }
      MaterializedTableMeta tableMeta = new MaterializedTableMeta(
          name, columnsBuilder.build(), metaRepo);
      for (BasicColumn column : tableMeta.columns().values()) {
        if (column.isUnique()) {
          if (column.getType() != DataTypes.StringType && column.getType() != DataTypes.LongType) {
            throw new ModelException("Unique indexes are only supported for " + DataTypes.StringType +
                " and " + DataTypes.LongType + " types, not " + column.getType() + ".");
          } else {
            metaRepo.registerUniqueIndex(tableMeta, column);
          }
        }
      }

      ImmutableMap.Builder<String, DerivedColumn> simpleChecksBuilder = ImmutableMap.builder();
      PrimaryKeyDefinition primaryKey = null;
      ImmutableMap.Builder<String, ForeignKey> foreignKeysBuilder = ImmutableMap.builder();
      for (ConstraintDefinition constraint : constraints) {
        if (tableMeta.columns().containsKey(constraint.getName())) {
          throw new ModelException("Name collision for constraint '" + constraint.getName() + "'.");
        }
        if (constraint instanceof SimpleCheckDefinition) {
          SimpleCheckDefinition simpleCheck = (SimpleCheckDefinition) constraint;
          DerivedColumn constraintColumn = simpleCheck.getExpr().compile(tableMeta, metaRepo);
          if (constraintColumn.getType() != DataTypes.BoolType) {
            throw new ModelException("Constraint expression has to be boolean.");
          }
          simpleChecksBuilder.put(simpleCheck.getName(), constraintColumn);
        } else if (constraint instanceof PrimaryKeyDefinition) {
          if (primaryKey == null) {
            primaryKey = (PrimaryKeyDefinition) constraint;
            BasicColumn keyColumn = (BasicColumn) tableMeta.column(primaryKey.getKeyColumn());
            keyColumn.setNullable(false);
            keyColumn.setUnique(true);
            keyColumn.setIndexed(true);
            metaRepo.registerUniqueIndex(tableMeta, keyColumn);
            tableMeta.setPrimaryKey(Optional.of(new PrimaryKey(primaryKey.getName(), keyColumn)));
          } else {
            throw new ModelException("There can only be one primary key.");
          }
        } else if (constraint instanceof ForeignKeyDefinition) {
          ForeignKeyDefinition foreignKey = (ForeignKeyDefinition) constraint;
          BasicColumn keyColumn = (BasicColumn) tableMeta.column(foreignKey.getKeyColumn());
          MaterializedTableMeta refTable = metaRepo.table(foreignKey.getRefTable());
          BasicColumn refColumn = refTable.columns().get(foreignKey.getRefColumn().getString());
          if (!refColumn.isUnique()) {
            throw new ModelException("Foreign key reference column has to be unique.");
          }
          keyColumn.setIndexed(true);
          if (!keyColumn.isUnique()) {
            // Unique keys have an index by default.
            metaRepo.registerMultiIndex(tableMeta, keyColumn);
          }
          foreignKeysBuilder.put(foreignKey.getName(),
              new ForeignKey(foreignKey.getName(), keyColumn, refTable, refColumn));
          refTable.setReverseForeignKeys(ImmutableMap.<String, ForeignKey>builder()
              .putAll(refTable.getReverseForeignKeys())
              .put(
                  foreignKey.getName(),
                  new ForeignKey(foreignKey.getName(), refColumn, tableMeta, keyColumn))
              .build());
        }
      }
      tableMeta.setSimpleChecks(simpleChecksBuilder.build());
      tableMeta.setForeignKeys(foreignKeysBuilder.build());
      metaRepo.registerTable(name, tableMeta);
      return new MetaStatementResult();
    }

    @Override
    public void rollback() {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void commit() throws IOException {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void collectLocks(List<ResourceLock> locks) {
      // TODO Auto-generated method stub
      
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class ColumnDefinition extends Node {
    private final String name;
    private final DataType<?> type;
    private final boolean nullable;
    private final boolean unique;
  }

  public interface ConstraintDefinition {
    public String getName();
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class SimpleCheckDefinition extends Node implements ConstraintDefinition {
    private final String name;
    private final Expression expr;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class PrimaryKeyDefinition extends Node implements ConstraintDefinition {
    private final String name;
    private final Ident keyColumn;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class ForeignKeyDefinition extends Node implements ConstraintDefinition {
    private final String name;
    private final Ident keyColumn;
    private final Ident refTable;
    private final Ident refColumn;
  }
}
