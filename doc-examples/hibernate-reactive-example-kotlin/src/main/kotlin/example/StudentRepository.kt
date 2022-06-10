package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import javax.transaction.Transactional

@Repository
interface StudentRepository : CoroutineCrudRepository<Student, Long> {

    suspend fun testUpdateOptimisticLock(student: Student) {
        saveNewStudent(student)
        updateStudent(student.id!!)
    }

    suspend fun testDeleteOptimisticLock(student: Student) {
        saveNewStudent(student)
        deleteStudent(student.id!!)
    }

    suspend fun testMergeOptimisticLock(student: Student) {
        saveNewStudent(student)
        mergeStudent(student.id!!)
    }

    @Transactional
    suspend fun updateStudent(id: Long) {
        val student = findById(id)!!
        concurrentStudentUpdate(id)
        student.name = "Xyz"
    }

    @Transactional
    suspend fun deleteStudent(id: Long) {
        println("LOADED")
        val student = findById(id)!!
        concurrentStudentUpdate(id)
        println("DELETE")
        delete(student)
    }

    @Transactional
    suspend fun mergeStudent(id: Long) {
        val student = findById(id)!!
        concurrentStudentUpdate(id)
        student.name = "Xyz"
        update(student)
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    suspend fun concurrentStudentUpdate(id: Long) {
        val student = findById(id)!!
        student.name = "Abc"
        println("Updated")
    }

    @Transactional
    suspend fun saveNewStudent(student: Student): Long {
        return save(student).id!!
    }


}
