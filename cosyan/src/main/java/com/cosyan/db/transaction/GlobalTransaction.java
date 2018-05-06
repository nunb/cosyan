package com.cosyan.db.transaction;

import java.io.IOException;

import com.cosyan.db.auth.AuthToken;
import com.cosyan.db.lang.expr.SyntaxTree.GlobalStatement;
import com.cosyan.db.lang.transaction.Result;
import com.cosyan.db.lang.transaction.Result.CrashResult;
import com.cosyan.db.meta.Grants.GrantException;
import com.cosyan.db.meta.MetaRepo;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.session.Session;

public class GlobalTransaction extends MetaTransaction {

  private final GlobalStatement globalStatement;

  public GlobalTransaction(long trxNumber, GlobalStatement globalStatement) {
    super(trxNumber);
    this.globalStatement = globalStatement;
  }

  @Override
  protected Result execute(MetaRepo metaRepo, Resources resources) {
    return Result.META_OK;
  }

  public Result innerExecute(MetaRepo metaRepo, Session session) {
    try {
      return globalStatement.execute(metaRepo, session.authToken());
    } catch (IOException | ModelException | GrantException e) {
      return new CrashResult(e);
    }
  }

  @Override
  protected MetaResources collectResources(MetaRepo metaRepo, AuthToken authToken)
      throws ModelException, GrantException, IOException {
    globalStatement.execute(metaRepo, authToken);
    return MetaResources.empty();
  }
}
