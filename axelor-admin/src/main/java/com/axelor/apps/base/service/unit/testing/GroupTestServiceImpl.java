/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.base.service.unit.testing;

import com.axelor.apps.base.db.GroupTest;
import com.axelor.apps.base.db.GroupTestLine;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class GroupTestServiceImpl implements GroupTestService {

  protected GroupTestLineService groupTestLineService;

  @Inject
  public GroupTestServiceImpl(GroupTestLineService groupTestLineService) {
    this.groupTestLineService = groupTestLineService;
  }

  @Override
  @Transactional(rollbackOn = Exception.class)
  public void generate(GroupTest groupTest) {
    for (GroupTestLine groupTestLine : getSortedGroupTestLines(groupTest)) {
      groupTestLineService.generateScript(groupTestLine);
    }
  }

  @Override
  public String execute(GroupTest groupTest) {
    List<Pair<String, String>> resultList = new LinkedList<>();
    for (GroupTestLine groupTestLine : getSortedGroupTestLines(groupTest)) {
      resultList.add(groupTestLineService.execute(groupTestLine));
    }
    return processResult(resultList);
  }

  protected List<GroupTestLine> getSortedGroupTestLines(GroupTest groupTest) {
    List<GroupTestLine> groupTestLineList = groupTest.getGroupTestLineList();
    groupTestLineList.sort(Comparator.comparingInt(GroupTestLine::getSequence));
    return groupTestLineList;
  }

  protected String processResult(List<Pair<String, String>> resultList) {
    StringBuilder resultBuilder = new StringBuilder();
    resultBuilder.append("<ul>");
    for (Pair<String, String> entry : resultList) {
      resultBuilder.append("<li>");
      String name = String.format("<b>%s</b><br/>", entry.getLeft());
      String result = String.format("<span>%s</span>", entry.getRight());
      resultBuilder.append(name);
      resultBuilder.append(result);
      resultBuilder.append("</li><br/>");
    }
    resultBuilder.append("</ul>");
    return resultBuilder.toString();
  }
}
