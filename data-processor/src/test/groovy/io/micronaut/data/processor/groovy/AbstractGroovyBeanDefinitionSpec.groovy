package io.micronaut.data.processor.groovy

import groovy.transform.CompileStatic
import io.micronaut.ast.groovy.utils.InMemoryByteCodeGroovyClassLoader
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

abstract class AbstractGroovyBeanDefinitionSpec extends Specification{

    @CompileStatic
    BeanDefinition buildBeanDefinition(String className, String classStr) {
        def beanDefName= '$' + NameUtils.getSimpleName(className) + 'Definition'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        def classLoader = new InMemoryByteCodeGroovyClassLoader()
        classLoader.parseClass(classStr)
        return (BeanDefinition)classLoader.loadClass(beanFullName).newInstance()
    }

}
