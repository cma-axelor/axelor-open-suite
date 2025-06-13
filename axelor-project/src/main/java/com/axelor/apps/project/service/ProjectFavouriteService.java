package com.axelor.apps.project.service;

import com.axelor.apps.project.db.Project;
import com.axelor.auth.db.User;

public interface ProjectFavouriteService {

  void addToCurrentUserToFavProject(Project project);

  void addToFavProject(Project project, User user);

  void removeCurrentUserFromFavProject(Project project);

  void removeCurrentUserFromFavProject(Project project, User user);
}
