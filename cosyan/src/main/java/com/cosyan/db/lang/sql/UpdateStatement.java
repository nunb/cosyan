package com.cosyan.db.lang.sql;

import java.io.IOException;
import java.util.Optional;

import com.cosyan.db.io.TableWriter;
import com.cosyan.db.lang.expr.Expression;
import com.cosyan.db.lang.sql.Result.StatementResult;
import com.cosyan.db.lang.sql.SyntaxTree.Node;
import com.cosyan.db.lang.sql.SyntaxTree.Statement;
import com.cosyan.db.logic.PredicateHelper;
import com.cosyan.db.logic.PredicateHelper.VariableEquals;
import com.cosyan.db.meta.MetaRepo;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.meta.MetaRepo.RuleException;
import com.cosyan.db.model.ColumnMeta;
import com.cosyan.db.model.Ident;
import com.cosyan.db.model.MaterializedTableMeta;
import com.cosyan.db.model.MaterializedTableMeta.SeekableTableMeta;
import com.cosyan.db.transaction.MetaResources;
import com.cosyan.db.transaction.Resources;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import lombok.Data;
import lombok.EqualsAndHashCode;

public class UpdateStatement {

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class SetExpression extends Node {
    private final Ident ident;
    private final Expression value;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class Update extends Node implements Statement {
    private final Ident table;
    private final ImmutableList<SetExpression> updates;
    private final Optional<Expression> where;

    private SeekableTableMeta tableMeta;
    private ColumnMeta whereColumn;
    private ImmutableMap<Integer, ColumnMeta> columnExprs;
    private VariableEquals clause;

    @Override
    public MetaResources compile(MetaRepo metaRepo) throws ModelException {
      MaterializedTableMeta materializedTableMeta = metaRepo.table(table);
      tableMeta = materializedTableMeta.reader();
      ImmutableMap.Builder<Integer, ColumnMeta> columnExprsBuilder = ImmutableMap.builder();
      for (SetExpression update : updates) {
        ColumnMeta columnExpr = update.getValue().compileColumn(tableMeta);
        columnExprsBuilder.put(tableMeta.column(update.getIdent()).index(), columnExpr);
      }
      columnExprs = columnExprsBuilder.build();
      if (where.isPresent()) {
        whereColumn = where.get().compileColumn(tableMeta);
        clause = PredicateHelper.getBestClause(tableMeta, where.get());
      } else {
        whereColumn = ColumnMeta.TRUE_COLUMN;
      }
      return MetaResources.updateTable(materializedTableMeta)
          .merge(materializedTableMeta.ruleDependenciesReadResources())
          .merge(materializedTableMeta.reverseRuleDependenciesReadResources());
    }

    @Override
    public Result execute(Resources resources) throws RuleException, IOException {
      // The rules must be re-evaluated for updated records. In addition, rules of other
      // tables referencing this table have to be re-evaluated as well. We need the rule
      // dependencies for the rules of this table and referencing rules.
      TableWriter writer = resources.writer(tableMeta.tableName());
      if (clause == null) {
        return new StatementResult(writer.update(resources, columnExprs, whereColumn));
      } else {
        return new StatementResult(writer.updateWithIndex(resources, columnExprs, whereColumn, clause));
      }
    }

    @Override
    public void cancel() {

    }
  }
}
