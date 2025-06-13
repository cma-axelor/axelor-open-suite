package com.axelor.apps.project.service;

import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaAction;
import com.axelor.meta.db.MetaMenu;
import com.axelor.meta.db.repo.MetaActionRepository;
import com.axelor.meta.db.repo.MetaMenuRepository;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.utils.helpers.MetaActionHelper;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class ProjectFavouriteServiceImpl implements ProjectFavouriteService {

  private static final String MENU_NAME_PREFIX = "project-fav-project-";

  protected final ProjectRepository projectRepository;
  protected final MetaMenuRepository metaMenuRepository;
  protected final MetaActionRepository metaActionRepository;

  @Inject
  public ProjectFavouriteServiceImpl(
      ProjectRepository projectRepository,
      MetaMenuRepository metaMenuRepository,
      MetaActionRepository metaActionRepository) {
    this.projectRepository = projectRepository;
    this.metaMenuRepository = metaMenuRepository;
    this.metaActionRepository = metaActionRepository;
  }

  @Override
  public void addToCurrentUserToFavProject(Project project) {
    addToFavProject(project, AuthUtils.getUser());
  }

  @Override
  @Transactional
  public void addToFavProject(Project project, User user) {
    //    createMenu(project, user);
    project.addFavouriteProjectUserSetItem(user);
    projectRepository.save(project);
  }

  protected void createMenu(Project project, User user) {
    String name = MENU_NAME_PREFIX + user.getId() + "-" + project.getId();
    String subName = name.replaceAll("[-\\s]", ".");
    String title = project.getName();

    MetaAction metaAction = createMetaAction("action." + subName, title, project);
    MetaMenu menu =
        createMetaMenu(
            name, title, metaAction, metaMenuRepository.findByName("project-fav-project"));

    metaMenuRepository.save(menu);
  }

  @Override
  public void removeCurrentUserFromFavProject(Project project) {
    removeCurrentUserFromFavProject(project, AuthUtils.getUser());
  }

  @Override
  @Transactional
  public void removeCurrentUserFromFavProject(Project project, User user) {
    //    removeMenu(project, user);
    project.removeFavouriteProjectUserSetItem(user);
    projectRepository.save(project);
  }

  protected void removeMenu(Project project, User user) {
    String name = MENU_NAME_PREFIX + user.getId() + "-" + project.getId();
    MetaMenu menu = metaMenuRepository.findByName(name);
    if (menu != null) {
      MetaAction action = menu.getAction();
      metaActionRepository.remove(action);
      metaMenuRepository.remove(menu);
      MetaStore.invalidate(action.getName());
    }
  }

  protected MetaMenu createMetaMenu(
      String name, String title, MetaAction metaAction, MetaMenu parentMenu) {

    MetaMenu metaMenu = new MetaMenu();
    metaMenu.setName(name);
    metaMenu.setAction(metaAction);
    metaMenu.setModule("axelor-project");
    metaMenu.setTitle(title);
    metaMenu.setParent(parentMenu);

    return metaMenu;
  }

  protected MetaAction createMetaAction(String name, String title, Project project) {

    String module = "axelor-project";
    String type = "action-view";

    ActionView actionView =
        ActionView.define(title)
            .name(name)
            .model(Project.class.getName())
            .add("grid", "project-tree-grid")
            .add("form", "project-form")
            .domain("self.id = :id")
            .context("id", project.getId().toString())
            .get();

    return MetaActionHelper.actionToMetaAction(actionView, name, type, module);
  }
}
