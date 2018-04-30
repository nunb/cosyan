package com.cosyan.db.lang.sql;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.cosyan.db.UnitTestBase;
import com.cosyan.db.lang.transaction.Result.ErrorResult;
import com.cosyan.db.lang.transaction.Result.QueryResult;
import com.cosyan.db.meta.MaterializedTableMeta;
import com.cosyan.db.model.BasicColumn;
import com.cosyan.db.model.DataTypes;
import com.cosyan.db.model.Ident;

public class AlterStatementTest extends UnitTestBase {

  @Test
  public void testDropColumn() throws Exception {
    execute("create table t1 (a varchar, b integer, c float);");
    execute("alter table t1 drop b;");
    MaterializedTableMeta tableMeta = metaRepo.table(new Ident("t1"));
    assertEquals(2, tableMeta.columns().size());
    assertEquals(new BasicColumn(0, new Ident("a"), DataTypes.StringType, true, false, false),
        tableMeta.column(new Ident("a")));
    assertEquals(new BasicColumn(2, new Ident("c"), DataTypes.DoubleType, true, false, false),
        tableMeta.column(new Ident("c")));
  }

  @Test
  public void testDropColumnTestData() throws Exception {
    execute("create table t2 (a varchar, b integer, c float);");
    execute("insert into t2 values('x', 1, 1.0);");
    execute("insert into t2 values('y', 2, 2.0);");
    execute("alter table t2 drop b;");
    {
      QueryResult result = query("select * from t2;");
      assertHeader(new String[] { "a", "c" }, result);
      assertValues(new Object[][] { { "x", 1.0 }, { "y", 2.0 } }, result);
    }

    execute("insert into t2 values('z', 3.0);");
    {
      QueryResult result = query("select * from t2;");
      assertHeader(new String[] { "a", "c" }, result);
      assertValues(new Object[][] { { "x", 1.0 }, { "y", 2.0 }, { "z", 3.0 } }, result);
    }
  }

  @Test
  public void testDropColumnWithConstraints() throws Exception {
    execute("create table t3 (a integer, b integer, constraint pk_b primary key (b), constraint c_a check(a > 1));");
    {
      ErrorResult result = error("alter table t3 drop a;");
      assertEquals("[20, 21]: Cannot drop column 'a', check 'c_a [(a > 1)]' fails.\n" +
          "[1, 2]: Column 'a' not found in table 't3'.", result.getError().getMessage());
    }
    assertEquals(false, metaRepo.table(new Ident("t3")).column(new Ident("a")).isDeleted());
    execute("create table t4 (a integer, constraint fk_a foreign key (a) references t3(b));");
    {
      ErrorResult result = error("alter table t4 drop a;");
      assertEquals("[20, 21]: Cannot drop column 'a', it is used by foreign key 'fk_a [a -> t3.b]'.",
          result.getError().getMessage());
    }
    assertEquals(false, metaRepo.table(new Ident("t4")).column(new Ident("a")).isDeleted());
    {
      ErrorResult result = error("alter table t3 drop b;");
      assertEquals("[20, 21]: Cannot drop column 'b', it is used by reverse foreign key 'rev_fk_a [t4.a -> b]'.",
          result.getError().getMessage());
    }
    assertEquals(false, metaRepo.table(new Ident("t3")).column(new Ident("b")).isDeleted());
  }

  @Test
  public void testAddColumn() throws Exception {
    execute("create table t5 (a varchar, b integer);");
    execute("alter table t5 add c float;");
    MaterializedTableMeta tableMeta = metaRepo.table(new Ident("t5"));
    assertEquals(3, tableMeta.columns().size());
    assertEquals(new BasicColumn(0, new Ident("a"), DataTypes.StringType, true, false, false),
        tableMeta.column(new Ident("a")));
    assertEquals(new BasicColumn(1, new Ident("b"), DataTypes.LongType, true, false, false),
        tableMeta.column(new Ident("b")));
    assertEquals(new BasicColumn(2, new Ident("c"), DataTypes.DoubleType, true, false, false),
        tableMeta.column(new Ident("c")));
  }

  @Test
  public void testAddColumnTestData() throws Exception {
    execute("create table t6 (a varchar, b integer);");
    execute("insert into t6 values('x', 1);");
    execute("insert into t6 values('y', 2);");
    execute("alter table t6 add c float;");
    {
      QueryResult result = query("select * from t6;");
      assertHeader(new String[] { "a", "b", "c" }, result);
      assertValues(new Object[][] {
          { "x", 1L, null },
          { "y", 2L, null } }, result);
    }

    execute("insert into t6 values('z', 3, 3.0);");
    {
      QueryResult result = query("select * from t6;");
      assertHeader(new String[] { "a", "b", "c" }, result);
      assertValues(new Object[][] {
          { "x", 1L, null },
          { "y", 2L, null },
          { "z", 3L, 3.0 } }, result);
    }
  }

  @Test
  public void testAddColumnErrors() throws Exception {
    execute("create table t7 (a varchar);");
    {
      ErrorResult result = error("alter table t7 add a varchar;");
      assertEquals("[19, 20]: Duplicate column, foreign key or reversed foreign key name in 't7': 'a'.",
          result.getError().getMessage());
    }
    assertEquals(1, metaRepo.table(new Ident("t7")).columns().size());
    execute("insert into t7 values ('x');");
    {
      ErrorResult result = error("alter table t7 add b varchar not null;");
      assertEquals("[19, 20]: Cannot add column 'b', new columns on a non empty table have to be nullable.",
          result.getError().getMessage());
    }
    assertEquals(1, metaRepo.table(new Ident("t7")).columns().size());
  }

  @Test
  public void testDropThenAddColumnWithSameName() throws Exception {
    execute("create table t8 (a varchar, b integer);");
    execute("insert into t8 values('x', 1);");
    execute("alter table t8 drop b;");
    execute("insert into t8 values('y');");
    execute("alter table t8 add b float;");
    execute("insert into t8 values('z', 1.0);");
    QueryResult result = query("select * from t8;");
    assertHeader(new String[] { "a", "b" }, result);
    assertValues(new Object[][] {
        { "x", null },
        { "y", null },
        { "z", 1.0 } }, result);
  }

  @Test
  public void testAddThenDropColumn() throws Exception {
    execute("create table t9 (a varchar, b integer);");
    execute("insert into t9 values('x', 1);");
    execute("alter table t9 add c float;");
    execute("insert into t9 values('y', 2, 2.0);");
    execute("alter table t9 drop b;");
    execute("insert into t9 values('z', 3.0);");
    QueryResult result = query("select * from t9;");
    assertHeader(new String[] { "a", "c" }, result);
    assertValues(new Object[][] {
        { "x", null },
        { "y", 2.0 },
        { "z", 3.0 } }, result);
  }

  @Test
  public void testAlterColumnErrors() throws Exception {
    execute("create table t10 (a varchar, b varchar, c varchar);");
    {
      ErrorResult result = error("alter table t10 alter d varchar;");
      assertEquals("[22, 23]: Column 'd' not found in table 't10'.", result.getError().getMessage());
    }
    {
      ErrorResult result = error("alter table t10 alter a integer;");
      assertEquals("[22, 23]: Cannot alter column 'a', type has to remain the same.", result.getError().getMessage());
    }
    execute("insert into t10 values ('x', 'y', 'z');");
    {
      ErrorResult result = error("alter table t10 alter b varchar unique;");
      assertEquals("[22, 23]: Cannot alter column 'b', uniqueness has to remain the same.",
          result.getError().getMessage());
    }
    {
      ErrorResult result = error("alter table t10 alter c varchar not null;");
      assertEquals("[22, 23]: Cannot alter column 'c', column has to remain nullable.", result.getError().getMessage());
    }
  }

  @Test
  public void testAlterColumnLiftConstraint() throws Exception {
    execute("create table t11 (a varchar, b float not null);");
    execute("insert into t11 values('x', 1.0);");
    execute("insert into t11 values('y', 2.0);");
    execute("alter table t11 alter b float;");
    execute("insert into t11 (a) values('z');");

    QueryResult result = query("select * from t11;");
    assertHeader(new String[] { "a", "b" }, result);
    assertValues(new Object[][] {
        { "x", 1.0 },
        { "y", 2.0 },
        { "z", null } }, result);
  }

  @Test
  public void testQueryDroppedColumn() throws Exception {
    execute("create table t12 (a varchar, b integer);");
    execute("alter table t12 drop b;");
    ErrorResult result = error("select a, b from t12;");
    assertEquals("[10, 11]: Column 'b' not found in table 't12'.", result.getError().getMessage());
  }

  @Test
  public void testAlterTableAddConstraint() throws Exception {
    execute("create table t13 (a integer);");
    execute("alter table t13 add constraint c1 check (a > 0);");
    ErrorResult result = error("insert into t13 values (0);");
    assertEquals("Constraint check c1 failed.", result.getError().getMessage());
  }
}
