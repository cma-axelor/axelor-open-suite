package com.axelor.apps.account.service;

import com.axelor.db.Model;
import java.util.List;

public interface MassUpdateService {

  public <T extends Model> Integer massUpdate(
      String metaModel, int statusSelect, List<Integer> selectedIds) throws Exception;
}
