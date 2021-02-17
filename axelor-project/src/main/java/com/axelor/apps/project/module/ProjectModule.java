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
package com.axelor.apps.project.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.project.db.repo.ProjectManagementRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectTaskProjectRepository;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.apps.project.db.repo.ProjectTemplateManagementRepository;
import com.axelor.apps.project.db.repo.ProjectTemplateRepository;
import com.axelor.apps.project.db.repo.TeamProjectRepository;
import com.axelor.apps.project.service.ProjectService;
import com.axelor.apps.project.service.ProjectServiceImpl;
import com.axelor.apps.project.service.ProjectTaskService;
import com.axelor.apps.project.service.ProjectTaskServiceImpl;
import com.axelor.apps.project.service.ResourceBookingService;
import com.axelor.apps.project.service.ResourceBookingServiceImpl;
import com.axelor.apps.project.service.TimerProjectTaskService;
import com.axelor.apps.project.service.TimerProjectTaskServiceImpl;
import com.axelor.apps.project.service.app.AppProjectService;
import com.axelor.apps.project.service.app.AppProjectServiceImpl;
import com.axelor.team.db.repo.TeamRepository;

public class ProjectModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(ProjectRepository.class).to(ProjectManagementRepository.class);
    bind(ProjectTemplateRepository.class).to(ProjectTemplateManagementRepository.class);
    bind(AppProjectService.class).to(AppProjectServiceImpl.class);
    bind(ProjectTaskRepository.class).to(ProjectTaskProjectRepository.class);
    bind(ProjectService.class).to(ProjectServiceImpl.class);
    bind(ProjectTaskService.class).to(ProjectTaskServiceImpl.class);
    bind(TeamRepository.class).to(TeamProjectRepository.class);
    bind(TimerProjectTaskService.class).to(TimerProjectTaskServiceImpl.class);
    bind(ResourceBookingService.class).to(ResourceBookingServiceImpl.class);
  }
}
