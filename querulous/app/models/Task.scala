package models

import java.util.{Date}

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

import java.sql.ResultSet
import DBStuff.queryEvaluator

case class Task(id: Pk[Long], folder: String, project: Long, title: String, done: Boolean, dueDate: Option[Date], assignedTo: Option[String])

object Task {
  
  // -- Parsers
  
  /**
   * Parse a Task from a ResultSet
   */
  val simple = {
    get[Pk[Long]]("task.id") ~
    get[String]("task.folder") ~
    get[Long]("task.project") ~
    get[String]("task.title") ~
    get[Boolean]("task.done") ~
    get[Option[Date]]("task.due_date") ~
    get[Option[String]]("task.assigned_to") map {
      case id~folder~project~title~done~dueDate~assignedTo => Task(
        id, folder, project, title, done, dueDate, assignedTo
      )
    }
  }

  val simpleQ = { row: ResultSet =>
    Task(
      Id(row.getLong("task.id")),
      row.getString("task.folder"),
      row.getLong("task.project"),
      row.getString("task.title"),
      row.getBoolean("task.done"),
      Option.apply(row.getDate("task.due_date")),
      Option.apply(row.getString("task.assigned_to"))
    )
  }
  
  // -- Queries
  
  /**
   * Retrieve a Task from the id.
   */
  def findById(id: Long): Option[Task] = {
    queryEvaluator.selectOne("select * from task where id = ?", id)(simpleQ)
  }
  
  /**
   * Retrieve todo tasks for the user.
   */
  def findTodoInvolving(user: String): Seq[(Task,Project)] = {
    queryEvaluator.select(
      """
          select * from task
          join project_member on project_member.project_id = task.project
          join project on project.id = project_member.project_id
          where task.done = false and project_member.user_email = ?
      """,
      user
    ){ row =>
      (simpleQ(row), Project.simpleQ(row))
    }
  }
  
  /**
   * Find tasks related to a project
   */
  def findByProject(project: Long): Seq[Task] = {
    queryEvaluator.select("select * from task where task.project = ?", project)(simpleQ)
  }

  /**
   * Delete a task
   */
  def delete(id: Long) {
    queryEvaluator.execute("delete from task where id = ?", id)
  }
  
  /**
   * Delete all task in a folder.
   */
  def deleteInFolder(projectId: Long, folder: String) {
    queryEvaluator.execute("delete from task where project = ? and folder = ?", projectId, folder)
  }
  
  /**
   * Mark a task as done or not
   */
  def markAsDone(taskId: Long, done: Boolean) {
    queryEvaluator.execute("update task set done = ? where id = ?", done, taskId)
  }
  
  /**
   * Rename a folder.
   */
  def renameFolder(projectId: Long, folder: String, newName: String) {
    queryEvaluator.execute("update task set folder = ? where folder = ? and project = ?", newName, folder, projectId)
  }
  
  /**
   * Check if a user is the owner of this task
   */
  def isOwner(task: Long, user: String): Boolean = {
    queryEvaluator.select(
      """
          select count(task.id) = 1 from task
          join project on task.project = project.id
          join project_member on project_member.project_id = project.id
          where project_member.user_email = ? and task.id = ?
      """,
      user, task
    ){ row =>
      row.getBoolean(1) // columns are 1-indexed
    }.head
  }

  /**
   * Create a Task.
   */
  def create(task: Task): Task = {
    val id = queryEvaluator.insert(
      """
        insert into task (title, done, due_date, assigned_to, project, folder)
        values (?, ?, ?, ?, ?, ?)
      """,
      task.title,
      task.done,
      task.dueDate,
      task.assignedTo,
      task.project,
      task.folder
    )

    task.copy(id = Id(id))
  }
  
}
