/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.csv.script;

import java.math.BigDecimal;

import com.axelor.apps.hr.service.employee.EmployeeService;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

public class ImportDurationStored {

	@Inject
	protected EmployeeService employeeService;

	public String getDurationHoursImport(String duration, String userImportId) throws AxelorException{
		
		if(StringUtils.isEmpty(duration) || StringUtils.isEmpty(userImportId)) {
			return duration;
		}
		
		BigDecimal visibleDuration = new BigDecimal(duration);
        User user = Beans.get(UserRepository.class).all().filter("self.importId = :importId")
                .bind("importId", Long.valueOf(userImportId)).fetchOne();
		BigDecimal durationStored = employeeService.getUserDuration(visibleDuration, user, true);
		return durationStored.toString();
	}
}
