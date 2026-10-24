package io.micronaut.data.runtime.config

import spock.lang.Specification

class SqlDialectOptionsConfigurationSpec extends Specification {

    void "the configured version wins over detection"() {
        given:
        def options = new SqlDialectOptionsConfiguration(version: version, detectVersion: detect)

        expect:
        options.resolveVersion({ '23.26' }) == resolved

        where:
        version | detect | resolved
        null    | false  | null
        null    | true   | '23.26'
        ''      | true   | '23.26'
        '19'    | true   | '19'
        '23.1'  | false  | '23.1'
    }

    void "detection is not attempted unless enabled"() {
        given:
        def options = new SqlDialectOptionsConfiguration()

        expect:
        options.resolveVersion({ throw new IllegalStateException('not expected') }) == null
    }

    void "the major and minor version are read from the version text"() {
        expect:
        SqlDialectOptionsConfiguration.parseMajorMinor(text) == version

        where:
        text                                                                                                        | version
        'Oracle AI Database 26ai Free Release 23.26.3.0.0 - Develop, Learn, and Run for Free Version 23.26.3.0.0' | '23.26'
        'Oracle Database 19c Enterprise Edition Release 19.0.0.0.0 - Production Version 19.28.0.0.0'            | '19.28'
        '23.26.3.0.0'                                                                                             | '23.26'
        'no version'                                                                                              | null
        null                                                                                                      | null
    }
}
