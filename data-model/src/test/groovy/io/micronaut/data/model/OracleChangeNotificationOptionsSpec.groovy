/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.model

import spock.lang.Specification

class OracleChangeNotificationOptionsSpec extends Specification {

    void "accepts supported option values and leaves unknown options to the driver: #name=#value"() {
        expect:
        OracleChangeNotificationOptions.invalidOption(name, value) == null

        where:
        name                     | value
        'DCN_NOTIFY_CHANGELAG'   | '0'
        'DCN_NOTIFY_CHANGELAG'   | ' 0 '
        'DCN_NOTIFY_ROWIDS'      | 'true'
        'DCN_NOTIFY_ROWIDS'      | 'TRUE'
        'NTF_TIMEOUT'            | '0'
        'NTF_TIMEOUT'            | '60'
        'NTF_TIMEOUT'            | '2147483647'
        'NTF_GROUPING_CLASS'     | 'NTF_GROUPING_CLASS_NONE'
        'DCN_PULL_NOTIFICATIONS' | 'false'
        'DCN_PULL_NOTIFICATIONS' | 'FALSE'
        'NTF_QOS_RELIABLE'       | 'true'
        'NEW_DRIVER_OPTION'      | 'driver-specific value'
    }

    void "preserves option parsing and diagnostic boundaries: #name=#value"() {
        expect:
        OracleChangeNotificationOptions.invalidOption(name, value) == error

        where:
        name                     | value                     | error
        ''                       | 'value'                   | 'has an Oracle property with a blank name'
        ' '                      | 'value'                   | 'has an Oracle property with a blank name'
        'NTF_TIMEOUT'            | '-1'                      | 'NTF_TIMEOUT must be a non-negative integer number of seconds'
        'NTF_TIMEOUT'            | '2147483648'              | 'NTF_TIMEOUT must be a non-negative integer number of seconds'
        'NTF_TIMEOUT'            | ' 60 '                    | 'NTF_TIMEOUT must be a non-negative integer number of seconds'
        'DCN_NOTIFY_ROWIDS'      | ' true '                  | 'requires DCN_NOTIFY_ROWIDS to be true so row-level operation and ROWID details are available'
        'DCN_NOTIFY_CHANGELAG'   | '00'                      | 'requires DCN_NOTIFY_CHANGELAG to be 0 so row-level operation and ROWID details are available'
        'NTF_GROUPING_CLASS'     | 'ntf_grouping_class_none' | 'NTF_GROUPING_CLASS: notification grouping is not supported'
        'DCN_PULL_NOTIFICATIONS' | ' false '                 | 'DCN_PULL_NOTIFICATIONS [ false ] is not supported because AQ pull delivery does not invoke the listener callback'
    }
}
