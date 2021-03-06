/*
 * Copyright 2018 Gergely Svigruha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cosyan.db.lock;

import org.junit.Test;

import com.cosyan.db.UnitTestBase;
import com.cosyan.db.lang.transaction.Result.QueryResult;
import com.cosyan.db.session.Session;
import com.google.common.collect.ImmutableList;

public class LockManagerTest extends UnitTestBase {

  private Thread runXTimes(String sql, int x) {
    return new Thread() {
      public void run() {
        Session s = dbApi.newAdminSession();
        for (int i = 0; i < x; i++) {
          s.execute(sql.replace("$x", String.valueOf(i)));
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    };
  }

  @Test
  public void testParallelUpdateOfOneTable() throws InterruptedException {
    Session s = dbApi.newAdminSession();
    s.execute("create table t1 (a integer);");
    s.execute("insert into t1 values (1);");
    {
      QueryResult result = query("select * from t1;", s);
      assertHeader(new String[] { "a" }, result);
      assertValues(new Object[][] { { 1L } }, result);
    }

    Thread t1 = runXTimes("update t1 set a = a + 1;", 100);
    Thread t2 = runXTimes("update t1 set a = a + 1;", 100);
    Thread t3 = runXTimes("update t1 set a = a + 1;", 100);

    t1.start();
    t2.start();
    t3.start();
    t1.join();
    t2.join();
    t3.join();

    QueryResult result = query("select * from t1;", s);
    assertHeader(new String[] { "a" }, result);
    assertValues(new Object[][] { { 301L } }, result);
  }

  @Test
  public void testParallelDeletesOfOneTable() throws InterruptedException {
    Session s = dbApi.newAdminSession();
    s.execute("create table t2 (a integer, b float, c varchar);");
    for (int i = 0; i < 100; i++) {
      s.execute("insert into t2 values (" + i + ", " + i + ".0, '" + i + "');");
    }
    {
      QueryResult result = query("select count(1) from t2;", s);
      assertValues(new Object[][] { { 100L } }, result);
    }

    Thread t1 = runXTimes("delete from t2 where a = $x * 4;", 25);
    Thread t2 = runXTimes("delete from t2 where a = $x * 4 + 1;", 25);
    Thread t3 = runXTimes("delete from t2 where a = $x * 4 + 2;", 25);

    t1.start();
    t2.start();
    t3.start();
    t1.join();
    t2.join();
    t3.join();

    {
      QueryResult result = query("select count(1) from t2;", s);
      assertValues(new Object[][] { { 25L } }, result);
    }
    {
      QueryResult result = query("select count(1) from t2 where a % 4 = 3;", s);
      assertValues(new Object[][] { { 25L } }, result);
    }
  }

  @Test
  public void testParallelSelectAndInserts() throws InterruptedException {
    Session s = dbApi.newAdminSession();
    s.execute("create table t3 (a integer, b varchar);");
    s.execute("create table t4 (x integer);");

    Thread t1 = runXTimes("insert into t3 values ($x, 'abc');" +
        "insert into t3 values ($x, 'abc');" +
        "insert into t3 values ($x, 'abc');" +
        "insert into t3 values ($x, 'abc');", 25);

    Thread t2 = runXTimes("insert into t3 values ($x, 'abc');" +
        "insert into t3 values ($x, 'abc');" +
        "insert into t3 values ($x, 'abc');" +
        "insert into t3 values ($x, 'abc');", 25);

    Thread t3 = new Thread() {
      public void run() {
        Session s = dbApi.newAdminSession();
        for (int i = 0; i < 25; i++) {
          QueryResult result = query("select count(1) from t3;", s);
          long cnt = (Long) result.getValues().get(0)[0];
          s.execute("insert into t4 values (" + cnt + ");");
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    };

    t1.start();
    t2.start();
    t3.start();
    t1.join();
    t2.join();
    t3.join();

    {
      QueryResult result = query("select count(1) from t4 where x % 4 = 0;", s);
      assertValues(new Object[][] { { 25L } }, result);
    }
    {
      QueryResult result = query("select min(x), max(x) from t4;", s);
      System.out.println("  min, max: " + ImmutableList.copyOf(result.getValues().get(0)));
      assert ((Long) result.getValues().get(0)[0] < (Long) result.getValues().get(0)[1]);
    }
  }

  @Test
  public void testParallelInsertWithIndex1() throws InterruptedException {
    Session s = dbApi.newAdminSession();
    s.execute("create table t5 (a integer, constraint pk_a primary key (a));");
    s.execute("create table t6 (a integer, constraint fk_a foreign key (a) references t5(a));");

    Thread t1 = runXTimes("insert into t5 values ($x);insert into t6 values ($x);", 100);
    Thread t2 = runXTimes("insert into t5 values ($x);insert into t6 values ($x);", 100);
    Thread t3 = runXTimes("insert into t5 values ($x);insert into t6 values ($x);", 100);

    t1.start();
    t2.start();
    t3.start();
    t1.join();
    t2.join();
    t3.join();

    // Only 100 inserts are expected to succeed. 200 fails due to index violations.
    {
      QueryResult result = query("select count(1) from t5;", s);
      assertValues(new Object[][] { { 100L } }, result);
    }
    {
      QueryResult result = query("select count(1) from t6;", s);
      assertValues(new Object[][] { { 100L } }, result);
    }
  }

  @Test
  public void testParallelInsertWithIndex2() throws InterruptedException {
    Session s = dbApi.newAdminSession();
    s.execute("create table t7 (a integer, constraint pk_a primary key (a));");
    s.execute("create table t8 (a integer, constraint fk_a foreign key (a) references t7(a));");
    s.execute("insert into t7 values(1000);");

    Thread t2 = runXTimes("insert into t8 values (1000);", 100);
    Thread t3 = runXTimes("insert into t7 values ($x);", 100);

    t2.start();
    t3.start();
    t2.join();
    t3.join();

    // Only 100 inserts are expected to succeed. 200 fails due to index violations.
    {
      QueryResult result = query("select count(1) from t7;", s);
      assertValues(new Object[][] { { 101L } }, result);
    }
    {
      QueryResult result = query("select count(1) from t8;", s);
      assertValues(new Object[][] { { 100L } }, result);
    }
  }

}
