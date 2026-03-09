/*
 * Copyright (c) 2022, 2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */
package io.micronaut.data.jakarta.tck;

import ee.jakarta.tck.data.framework.read.only.FruitPopulator;
import ee.jakarta.tck.data.standalone.entity.Coordinate;
import ee.jakarta.tck.data.standalone.entity.EntityTests;
import ee.jakarta.tck.data.standalone.entity.FruitSummary;
import ee.jakarta.tck.data.standalone.entity.JakartaDeleteQueryTests;
import ee.jakarta.tck.data.standalone.entity.JakartaQueryTests;
import ee.jakarta.tck.data.standalone.entity.JakartaUpdateQueryTests;
import ee.jakarta.tck.data.standalone.entity.MultipleEntityRepo;
import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.container.ClassContainer;


public class TCKArchiveProcessor implements ApplicationArchiveProcessor {

    @Override
    public void process(Archive<?> applicationArchive, TestClass testClass) {
        if (applicationArchive instanceof ClassContainer) {
            if (testClass.getName().equals(EntityTests.class.getName())) {
                ((ClassContainer<?>) applicationArchive).addClass(MultipleEntityRepo.class);
                ((ClassContainer<?>) applicationArchive).addClass(Coordinate.class);
            }
            if (testClass.getName().equals(JakartaDeleteQueryTests.class.getName())) {
                ((ClassContainer<?>) applicationArchive).addClass(FruitPopulator.class);
            }
            if (testClass.getName().equals(JakartaQueryTests.class.getName())) {
                ((ClassContainer<?>) applicationArchive).addClass(FruitPopulator.class);
                ((ClassContainer<?>) applicationArchive).addClass(FruitSummary.class);
            }
            if (testClass.getName().equals(JakartaUpdateQueryTests.class.getName())) {
                ((ClassContainer<?>) applicationArchive).addClass(FruitPopulator.class);
            }
        }
    }
}
