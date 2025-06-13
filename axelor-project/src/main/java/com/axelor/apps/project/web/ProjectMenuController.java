/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.project.web;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.service.exception.ErrorException;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.Sprint;
import com.axelor.apps.project.db.TaskStatus;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.apps.project.db.repo.TaskStatusRepository;
import com.axelor.apps.project.service.ProjectMenuService;
import com.axelor.apps.project.service.ProjectToolService;
import com.axelor.apps.project.service.roadmap.SprintGetService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.utils.helpers.ContextHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ProjectMenuController {

  public void allOpenProjectTasks(ActionRequest request, ActionResponse response) {
    Long projectId =
        Optional.of(request)
            .map(ActionRequest::getContext)
            .map(context -> context.get("projectId"))
            .map(Object::toString)
            .map(Long::valueOf)
            .orElse(null);
    Project project = projectId != null ? Beans.get(ProjectRepository.class).find(projectId) : null;
    response.setView(Beans.get(ProjectMenuService.class).getAllOpenProjectTasks(project));
  }

  public void allProjects(ActionRequest request, ActionResponse response) {
    Long projectId =
        Optional.of(request)
            .map(ActionRequest::getContext)
            .map(context -> context.get("childProjectId"))
            .map(Object::toString)
            .map(Long::valueOf)
            .orElse(null);

    response.setView(Beans.get(ProjectMenuService.class).getAllProjects(projectId));
  }

  public void allProjectTasks(ActionRequest request, ActionResponse response) {
    response.setView(Beans.get(ProjectMenuService.class).getAllProjectTasks());
  }

  public void allProjectTasksFiltered(ActionRequest request, ActionResponse response) {
    ActionViewBuilder builder =
        ActionView.define(I18n.get("All tasks"))
            .model(ProjectTask.class.getName())
            .add("grid", "project-task-editable-grid")
            .add("kanban", "project-task-kanban")
            .add("form", "project-task-form")
            .domain("self.typeSelect = :_typeSelect")
            .context("_typeSelect", ProjectTaskRepository.TYPE_TASK)
            .param("search-filters", "project-task-filters")
            .param("default-search-filters", "my-tasks");
    response.setView(builder.map());
  }

  public void myProjects(ActionRequest request, ActionResponse response) {
    Project activeProject =
        Optional.ofNullable(AuthUtils.getUser()).map(User::getActiveProject).orElse(null);

    ActionView.ActionViewBuilder builder =
        ActionView.define(I18n.get("Project"))
            .model(Project.class.getName())
            .add("grid", "project-grid")
            .add("form", "project-form")
            .add("kanban", "project-kanban")
            .domain(
                "(self.id IN :_projectIds OR :_project is null) AND :__user__ MEMBER OF self.membersUserSet")
            .context("_project", activeProject)
            .context("_projectIds", Beans.get(ProjectToolService.class).getActiveProjectIds())
            .param("search-filters", "project-project-filters");

    response.setView(builder.map());
  }

  @ErrorException
  public void allProjectRelatedTasks(ActionRequest request, ActionResponse response)
      throws AxelorException {

    Project project = null;
    if (Project.class.equals(request.getContext().getContextClass())) {
      project = request.getContext().asType(Project.class);
    } else {
      project = ContextHelper.getContextParent(request.getContext(), Project.class, 1);
    }

    if (project == null) {
      return;
    }
    project = Beans.get(ProjectRepository.class).find(project.getId());
    response.setView(Beans.get(ProjectMenuService.class).getAllProjectRelatedTasks(project));
  }

  public void viewTasksPerSprint(ActionRequest request, ActionResponse response) {
    Project project = request.getContext().asType(Project.class);
    SprintGetService sprintGetService = Beans.get(SprintGetService.class);
    List<Sprint> sprintList = sprintGetService.getSprintToDisplayIncludingBacklog(project);

    if (ObjectUtils.notEmpty(sprintList)) {
      String sprintIdsToExclude = sprintGetService.getSprintIdsToExclude(sprintList);

      ActionView.ActionViewBuilder actionViewBuilder =
          ActionView.define(I18n.get("Tasks per sprint"));
      actionViewBuilder.model(ProjectTask.class.getName());
      actionViewBuilder.add("kanban", "project-task-sprint-kanban");
      actionViewBuilder.add("form", "project-task-form");
      actionViewBuilder.param("kanban-hide-columns", sprintIdsToExclude);
      actionViewBuilder.domain("self.project.id = :_projectId");
      actionViewBuilder.context("_projectId", project.getId());

      response.setView(actionViewBuilder.map());
    }
  }

  public void viewSprints(ActionRequest request, ActionResponse response) {
    Project project = request.getContext().asType(Project.class);
    SprintGetService sprintGetService = Beans.get(SprintGetService.class);
    List<Sprint> sprintList = sprintGetService.getSprintToDisplay(project);
    List<Long> sprintIdList = List.of(0L);
    if (ObjectUtils.notEmpty(sprintList)) {
      sprintIdList = sprintList.stream().map(Sprint::getId).collect(Collectors.toList());
    }

    ActionView.ActionViewBuilder actionViewBuilder = ActionView.define(I18n.get("Sprints"));
    actionViewBuilder.model(Sprint.class.getName());
    actionViewBuilder.add("grid", "sprint-dashlet-grid");
    actionViewBuilder.add("form", "sprint-form");
    actionViewBuilder.domain("self.id IN (:sprintIds)");
    actionViewBuilder.context("sprintIds", sprintIdList);
    actionViewBuilder.context("sprintManagementSelect", project.getSprintManagementSelect());

    response.setView(actionViewBuilder.map());
  }

  public void allFavouriteProjects(ActionRequest request, ActionResponse response) {

    Set<Project> favouriteProjectSet = AuthUtils.getUser().getFavouriteProjectSet();
    ActionViewBuilder actionView =
        ActionView.define(I18n.get("Favourite projects"))
            .model(Project.class.getName())
            .add("tree", "project-phase-tree")
            .domain("self.id in :idList")
            .context("idList", getFavouriteProjectIdSet(favouriteProjectSet));
    response.setView(actionView.map());
  }

  protected Set<Long> getFavouriteProjectIdSet(Set<Project> favouriteProjectSet) {
    Set<Long> idSet = new HashSet<>();

    for (Project project : favouriteProjectSet) {
      if (project == null) continue;

      if (project.getParentProject() == null) {
        idSet.add(project.getId());
      } else {
        if (!isParentAlreadyInSet(project, idSet)) {
          idSet.add(project.getId());
        }
      }
    }

    return idSet;
  }

  protected boolean isParentAlreadyInSet(Project project, Set<Long> idSet) {
    Project current = project.getParentProject();
    while (current != null) {
      if (idSet.contains(current.getId())) {
        return true;
      }
      current = current.getParentProject();
    }
    return false;
  }

  public void allProjectTasksTree(ActionRequest request, ActionResponse response) {
    String sourceAction =
        Optional.ofNullable(request.getContext().get("_signal")).map(String::valueOf).orElse(null);
    if (sourceAction == null) {
      return;
    }
    boolean isTree = sourceAction.endsWith("treeBtn");
    boolean isList = sourceAction.endsWith("listBtn");

    Project project = request.getContext().asType(Project.class);
    ProjectRepository projectRepository = Beans.get(ProjectRepository.class);
    project = projectRepository.find(project.getId());

    ActionViewBuilder actionViewBuilder = null;
    if (isTree) {
      actionViewBuilder =
          ActionView.define(project.getName())
              .model(Project.class.getName())
              .add("grid", "project-tree-grid")
              .add("form", "project-form")
              .domain("self.id = :projectId")
              .context("projectId", project.getId());
    } else if (isList) {
      List<Long> ids = new ArrayList<>();
      List<Long> leafIds = new ArrayList<>();
      List<TaskStatus> statusToExclude = Beans.get(TaskStatusRepository.class).all().fetch();
      getAllProjectIds(project, ids, leafIds, statusToExclude);
      String statusToExcludStr =
          statusToExclude.stream()
              .map(TaskStatus::getId)
              .map(String::valueOf)
              .collect(Collectors.joining(","));
      actionViewBuilder =
          ActionView.define(project.getName())
              .model(ProjectTask.class.getName())
              .add("grid", "project-task-editable-grid")
              .add("kanban", "project-task-kanban")
              .add("form", "project-task-form")
              .domain("self.project.id IN :_projectIdList AND self.typeSelect = :_typeSelect")
              .context("_typeSelect", ProjectTaskRepository.TYPE_TASK)
              .context("_projectIdList", ids)
              .context(
                  "_project", leafIds.size() == 1 ? projectRepository.find(leafIds.get(0)) : null)
              .param("search-filters", "project-task-filters")
              .param("kanban-hide-columns", statusToExcludStr);
    } else {
      return;
    }

    response.setView(actionViewBuilder.map());
  }

  protected void getAllProjectIds(
      Project project, List<Long> allIds, List<Long> leafIds, List<TaskStatus> statusToExclude) {
    if (project == null) return;

    allIds.add(project.getId());
    project.getProjectTaskStatusSet().forEach(statusToExclude::remove);

    if (project.getChildProjectList() == null || project.getChildProjectList().isEmpty()) {
      leafIds.add(project.getId());
    } else {
      for (Project child : project.getChildProjectList()) {
        getAllProjectIds(child, allIds, leafIds, statusToExclude);
      }
    }
  }
}
