/*
 * Copyright (C) 2014 - 2017  Contributors as noted in the AUTHORS.md file
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package databases.sqlserver

import java.net.URI

import akka.testkit.{ TestActorRef, TestFSMRef }
import com.wegtam.scalatest.tags.{ DbTest, DbTestSqlServer }
import com.wegtam.tensei.adt._
import com.wegtam.tensei.agent.{ ActorSpec, DummyActor, TenseiAgent }
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.duration._

class ForeignKeysTest extends ActorSpec with BeforeAndAfterEach {

  val databaseHost = testConfig.getString("sqlserver.host")
  val databasePort = testConfig.getInt("sqlserver.port")
  val databaseName = testConfig.getString("sqlserver.target-db.name")
  val databaseUser = testConfig.getString("sqlserver.target-db.user")
  val databasePass = testConfig.getString("sqlserver.target-db.pass")

  override protected def beforeEach(): Unit = {
    // The database connection.
    val connection = java.sql.DriverManager
      .getConnection(s"jdbc:sqlserver://$databaseHost:$databasePort", databaseUser, databasePass)
    val s = connection.createStatement()
    s.execute(
      s"IF EXISTS(SELECT * FROM sys.databases WHERE name = '$databaseName') DROP DATABASE $databaseName"
    )
    s.execute(s"CREATE DATABASE $databaseName")
    s.close()
    connection.close()
    createSourceData()
    super.beforeEach()
  }

  override protected def afterEach(): Unit = {
    // The database connection.
    val connection = java.sql.DriverManager
      .getConnection(s"jdbc:sqlserver://$databaseHost:$databasePort", databaseUser, databasePass)
    val s = connection.createStatement()
    s.execute(
      s"IF EXISTS(SELECT * FROM sys.databases WHERE name = '$databaseName') DROP DATABASE $databaseName"
    )
    s.close()
    connection.close()
    super.afterEach()
  }

  private def createSourceData(): Unit = {
    val c = java.sql.DriverManager.getConnection(
      s"jdbc:sqlserver://$databaseHost:$databasePort;databasename=$databaseName",
      databaseUser,
      databasePass
    )
    val s = c.createStatement()
    s.execute("""
        |CREATE TABLE employees (
        |  id BIGINT PRIMARY KEY,
        |  firstname VARCHAR(254),
        |  lastname VARCHAR(254),
        |  birthday DATE
        |)
      """.stripMargin)
    s.execute("""
        |CREATE TABLE salary (
        |  employee_id BIGINT REFERENCES EMPLOYEES (id),
        |  amount DECIMAL(10,2)
        |)
      """.stripMargin)
    s.execute(
      """INSERT INTO employees (id, firstname, lastname, birthday) VALUES(123, 'Albert', 'Einstein', '1879-03-14')"""
    )
    s.execute("""INSERT INTO salary (employee_id, amount) VALUES(123, 3.14)""")
    s.execute(
      """INSERT INTO employees (id, firstname, lastname, birthday) VALUES(456, 'Bernhard', 'Riemann', '1826-09-17')"""
    )
    s.execute("""INSERT INTO salary (employee_id, amount) VALUES(456, 6.28)""")
    s.execute(
      """INSERT INTO employees (id, firstname, lastname, birthday) VALUES(789, 'Johann Carl Friedrich', 'Gauß', '1777-04-30')"""
    )
    s.execute("""INSERT INTO salary (employee_id, amount) VALUES(789, 12.56)""")
    s.execute(
      """INSERT INTO employees (id, firstname, lastname, birthday) VALUES(5, 'Johann Benedict', 'Listing', '1808-07-25')"""
    )
    s.execute("""INSERT INTO salary (employee_id, amount) VALUES(5, 25.12)""")
    s.execute(
      """INSERT INTO employees (id, firstname, lastname, birthday) VALUES(8, 'Gottfried Wilhelm', 'Leibnitz', '1646-07-01')"""
    )
    s.execute("""INSERT INTO salary (employee_id, amount) VALUES(8, 50.24)""")
    s.close()
    c.close()
  }

  describe("Foreign keys") {
    describe("using sqlserver") {
      describe("using one to one mappings") {
        describe("with single mappings") {
          it("should replace changed auto-increment values", DbTest, DbTestSqlServer) {
            val connection = java.sql.DriverManager.getConnection(
              s"jdbc:sqlserver://$databaseHost:$databasePort;databasename=$databaseName",
              databaseUser,
              databasePass
            )

            val sourceDfasdl = new DFASDL(
              id = "SRC",
              content = scala.io.Source
                .fromInputStream(
                  getClass.getResourceAsStream("/databases/generic/ForeignKeys/source-dfasdl.xml")
                )
                .mkString
            )
            val targetDfasdl = new DFASDL(
              id = "DST",
              content = scala.io.Source
                .fromInputStream(
                  getClass.getResourceAsStream("/databases/generic/ForeignKeys/target-dfasdl.xml")
                )
                .mkString
            )

            val cookbook: Cookbook = Cookbook(
              id = "COOKBOOK",
              sources = List(sourceDfasdl),
              target = Option(targetDfasdl),
              recipes = List(
                Recipe(
                  id = "CopyEmployees",
                  mode = Recipe.MapOneToOne,
                  mappings = List(
                    MappingTransformation(
                      sources = List(
                        ElementReference(dfasdlId = sourceDfasdl.id, elementId = "employees_row_id")
                      ),
                      targets = List(
                        ElementReference(dfasdlId = targetDfasdl.id, elementId = "employees_row_id")
                      )
                    ),
                    MappingTransformation(
                      sources = List(
                        ElementReference(dfasdlId = sourceDfasdl.id,
                                         elementId = "employees_row_firstname")
                      ),
                      targets = List(
                        ElementReference(dfasdlId = targetDfasdl.id,
                                         elementId = "employees_row_firstname")
                      )
                    ),
                    MappingTransformation(
                      sources = List(
                        ElementReference(dfasdlId = sourceDfasdl.id,
                                         elementId = "employees_row_lastname")
                      ),
                      targets = List(
                        ElementReference(dfasdlId = targetDfasdl.id,
                                         elementId = "employees_row_lastname")
                      )
                    ),
                    MappingTransformation(
                      sources = List(
                        ElementReference(dfasdlId = sourceDfasdl.id,
                                         elementId = "employees_row_birthday")
                      ),
                      targets = List(
                        ElementReference(dfasdlId = targetDfasdl.id,
                                         elementId = "employees_row_birthday")
                      )
                    )
                  )
                ),
                Recipe(
                  id = "CopySalaries",
                  mode = Recipe.MapOneToOne,
                  mappings = List(
                    MappingTransformation(
                      sources = List(
                        ElementReference(dfasdlId = sourceDfasdl.id,
                                         elementId = "salary_row_employee_id")
                      ),
                      targets = List(
                        ElementReference(dfasdlId = targetDfasdl.id,
                                         elementId = "salary_row_employee_id")
                      )
                    ),
                    MappingTransformation(
                      sources = List(
                        ElementReference(dfasdlId = sourceDfasdl.id,
                                         elementId = "salary_row_amount")
                      ),
                      targets = List(
                        ElementReference(dfasdlId = targetDfasdl.id,
                                         elementId = "salary_row_amount")
                      )
                    )
                  )
                )
              )
            )

            val source = ConnectionInformation(
              uri = new URI(connection.getMetaData.getURL.replace(" ", "_")),
              dfasdlRef =
                Option(DFASDLReference(cookbookId = cookbook.id, dfasdlId = sourceDfasdl.id)),
              username = Option(databaseUser),
              password = Option(databasePass)
            )
            val target = ConnectionInformation(
              uri = new URI(connection.getMetaData.getURL.replace(" ", "_")),
              dfasdlRef =
                Option(DFASDLReference(cookbookId = cookbook.id, dfasdlId = targetDfasdl.id)),
              username = Option(databaseUser),
              password = Option(databasePass)
            )

            val dummy  = TestActorRef(DummyActor.props())
            val client = system.actorSelection(dummy.path)
            val agent  = TestFSMRef(new TenseiAgent("TEST-AGENT", client))

            val msg = AgentStartTransformationMessage(
              sources = List(source),
              target = target,
              cookbook = cookbook,
              uniqueIdentifier = Option("FOREIGN-KEY-TEST-OneToOne")
            )

            agent ! msg

            expectMsgType[GlobalMessages.TransformationStarted](FiniteDuration(5, SECONDS))
            expectMsgType[GlobalMessages.TransformationCompleted](FiniteDuration(7, SECONDS))

            val s = connection.createStatement()
            withClue("Written data should be correct!") {
              val expectedData = Map(
                "Einstein" -> new java.math.BigDecimal("3.14"),
                "Riemann"  -> new java.math.BigDecimal("6.28"),
                "Gauß"     -> new java.math.BigDecimal("12.56"),
                "Listing"  -> new java.math.BigDecimal("25.12"),
                "Leibnitz" -> new java.math.BigDecimal("50.24")
              )
              val r = s.executeQuery(
                "SELECT t_employees.id AS id, t_employees.lastname AS name, t_salary.amount AS amount FROM t_employees JOIN t_salary ON t_employees.id = t_salary.employee_id"
              )
              if (r.next()) {
                r.getBigDecimal("amount") should be(expectedData(r.getString("name")))

                while (r.next()) {
                  r.getBigDecimal("amount") should be(expectedData(r.getString("name")))
                }
              } else
                fail("No results found in database!")

            }

            connection.close()
          }
        }

        describe("with bulk mappings") {
          it("should replace changed auto-increment values", DbTest, DbTestSqlServer) {
            val connection = java.sql.DriverManager.getConnection(
              s"jdbc:sqlserver://$databaseHost:$databasePort;databasename=$databaseName",
              databaseUser,
              databasePass
            )

            val sourceDfasdl = new DFASDL(
              id = "SRC",
              content = scala.io.Source
                .fromInputStream(
                  getClass.getResourceAsStream("/databases/generic/ForeignKeys/source-dfasdl.xml")
                )
                .mkString
            )
            val targetDfasdl = new DFASDL(
              id = "DST",
              content = scala.io.Source
                .fromInputStream(
                  getClass.getResourceAsStream("/databases/generic/ForeignKeys/target-dfasdl.xml")
                )
                .mkString
            )

            val cookbook: Cookbook = Cookbook(
              id = "COOKBOOK",
              sources = List(sourceDfasdl),
              target = Option(targetDfasdl),
              recipes = List(
                Recipe(
                  id = "CopyEmployees",
                  mode = Recipe.MapOneToOne,
                  mappings = List(
                    MappingTransformation(
                      sources = List(
                        ElementReference(dfasdlId = sourceDfasdl.id,
                                         elementId = "employees_row_id"),
                        ElementReference(dfasdlId = sourceDfasdl.id,
                                         elementId = "employees_row_firstname"),
                        ElementReference(dfasdlId = sourceDfasdl.id,
                                         elementId = "employees_row_lastname"),
                        ElementReference(dfasdlId = sourceDfasdl.id,
                                         elementId = "employees_row_birthday")
                      ),
                      targets = List(
                        ElementReference(dfasdlId = targetDfasdl.id,
                                         elementId = "employees_row_id"),
                        ElementReference(dfasdlId = targetDfasdl.id,
                                         elementId = "employees_row_firstname"),
                        ElementReference(dfasdlId = targetDfasdl.id,
                                         elementId = "employees_row_lastname"),
                        ElementReference(dfasdlId = targetDfasdl.id,
                                         elementId = "employees_row_birthday")
                      )
                    )
                  )
                ),
                Recipe(
                  id = "CopySalaries",
                  mode = Recipe.MapOneToOne,
                  mappings = List(
                    MappingTransformation(
                      sources = List(
                        ElementReference(dfasdlId = sourceDfasdl.id,
                                         elementId = "salary_row_employee_id"),
                        ElementReference(dfasdlId = sourceDfasdl.id,
                                         elementId = "salary_row_amount")
                      ),
                      targets = List(
                        ElementReference(dfasdlId = targetDfasdl.id,
                                         elementId = "salary_row_employee_id"),
                        ElementReference(dfasdlId = targetDfasdl.id,
                                         elementId = "salary_row_amount")
                      )
                    )
                  )
                )
              )
            )

            val source = ConnectionInformation(
              uri = new URI(connection.getMetaData.getURL.replace(" ", "_")),
              dfasdlRef =
                Option(DFASDLReference(cookbookId = cookbook.id, dfasdlId = sourceDfasdl.id)),
              username = Option(databaseUser),
              password = Option(databasePass)
            )
            val target = ConnectionInformation(
              uri = new URI(connection.getMetaData.getURL.replace(" ", "_")),
              dfasdlRef =
                Option(DFASDLReference(cookbookId = cookbook.id, dfasdlId = targetDfasdl.id)),
              username = Option(databaseUser),
              password = Option(databasePass)
            )

            val dummy  = TestActorRef(DummyActor.props())
            val client = system.actorSelection(dummy.path)
            val agent  = TestFSMRef(new TenseiAgent("TEST-AGENT", client))

            val msg = AgentStartTransformationMessage(
              sources = List(source),
              target = target,
              cookbook = cookbook,
              uniqueIdentifier = Option("FOREIGN-KEY-TEST-OneToOne")
            )

            agent ! msg

            expectMsgType[GlobalMessages.TransformationStarted](FiniteDuration(5, SECONDS))
            expectMsgType[GlobalMessages.TransformationCompleted](FiniteDuration(7, SECONDS))

            val s = connection.createStatement()
            withClue("Written data should be correct!") {
              val expectedData = Map(
                "Einstein" -> new java.math.BigDecimal("3.14"),
                "Riemann"  -> new java.math.BigDecimal("6.28"),
                "Gauß"     -> new java.math.BigDecimal("12.56"),
                "Listing"  -> new java.math.BigDecimal("25.12"),
                "Leibnitz" -> new java.math.BigDecimal("50.24")
              )
              val r = s.executeQuery(
                "SELECT t_employees.id AS id, t_employees.lastname AS name, t_salary.amount AS amount FROM t_employees JOIN t_salary ON t_employees.id = t_salary.employee_id"
              )
              if (r.next()) {
                r.getBigDecimal("amount") should be(expectedData(r.getString("name")))

                while (r.next()) {
                  r.getBigDecimal("amount") should be(expectedData(r.getString("name")))
                }
              } else
                fail("No results found in database!")
            }

            connection.close()
          }
        }
      }

      describe("using all to all mappings") {
        it("should replace changed auto-increment values", DbTest, DbTestSqlServer) {
          val connection = java.sql.DriverManager.getConnection(
            s"jdbc:sqlserver://$databaseHost:$databasePort;databasename=$databaseName",
            databaseUser,
            databasePass
          )

          val sourceDfasdl = new DFASDL(
            id = "SRC",
            content = scala.io.Source
              .fromInputStream(
                getClass.getResourceAsStream("/databases/generic/ForeignKeys/source-dfasdl.xml")
              )
              .mkString
          )
          val targetDfasdl = new DFASDL(
            id = "DST",
            content = scala.io.Source
              .fromInputStream(
                getClass.getResourceAsStream("/databases/generic/ForeignKeys/target-dfasdl.xml")
              )
              .mkString
          )

          val cookbook: Cookbook = Cookbook(
            id = "COOKBOOK",
            sources = List(sourceDfasdl),
            target = Option(targetDfasdl),
            recipes = List(
              Recipe(
                id = "CopyEmployees",
                mode = Recipe.MapAllToAll,
                mappings = List(
                  MappingTransformation(
                    sources = List(
                      ElementReference(dfasdlId = sourceDfasdl.id, elementId = "employees_row_id")
                    ),
                    targets = List(
                      ElementReference(dfasdlId = targetDfasdl.id, elementId = "employees_row_id")
                    )
                  ),
                  MappingTransformation(
                    sources = List(
                      ElementReference(dfasdlId = sourceDfasdl.id,
                                       elementId = "employees_row_firstname")
                    ),
                    targets = List(
                      ElementReference(dfasdlId = targetDfasdl.id,
                                       elementId = "employees_row_firstname")
                    )
                  ),
                  MappingTransformation(
                    sources = List(
                      ElementReference(dfasdlId = sourceDfasdl.id,
                                       elementId = "employees_row_lastname")
                    ),
                    targets = List(
                      ElementReference(dfasdlId = targetDfasdl.id,
                                       elementId = "employees_row_lastname")
                    )
                  ),
                  MappingTransformation(
                    sources = List(
                      ElementReference(dfasdlId = sourceDfasdl.id,
                                       elementId = "employees_row_birthday")
                    ),
                    targets = List(
                      ElementReference(dfasdlId = targetDfasdl.id,
                                       elementId = "employees_row_birthday")
                    )
                  )
                )
              ),
              Recipe(
                id = "CopySalaries",
                mode = Recipe.MapAllToAll,
                mappings = List(
                  MappingTransformation(
                    sources = List(
                      ElementReference(dfasdlId = sourceDfasdl.id,
                                       elementId = "salary_row_employee_id")
                    ),
                    targets = List(
                      ElementReference(dfasdlId = targetDfasdl.id,
                                       elementId = "salary_row_employee_id")
                    )
                  ),
                  MappingTransformation(
                    sources = List(
                      ElementReference(dfasdlId = sourceDfasdl.id, elementId = "salary_row_amount")
                    ),
                    targets = List(
                      ElementReference(dfasdlId = targetDfasdl.id, elementId = "salary_row_amount")
                    )
                  )
                )
              )
            )
          )

          val source = ConnectionInformation(
            uri = new URI(connection.getMetaData.getURL.replace(" ", "_")),
            dfasdlRef =
              Option(DFASDLReference(cookbookId = cookbook.id, dfasdlId = sourceDfasdl.id)),
            username = Option(databaseUser),
            password = Option(databasePass)
          )
          val target = ConnectionInformation(
            uri = new URI(connection.getMetaData.getURL.replace(" ", "_")),
            dfasdlRef =
              Option(DFASDLReference(cookbookId = cookbook.id, dfasdlId = targetDfasdl.id)),
            username = Option(databaseUser),
            password = Option(databasePass)
          )

          val dummy  = TestActorRef(DummyActor.props())
          val client = system.actorSelection(dummy.path)
          val agent  = TestFSMRef(new TenseiAgent("TEST-AGENT", client))

          val msg = AgentStartTransformationMessage(
            sources = List(source),
            target = target,
            cookbook = cookbook,
            uniqueIdentifier = Option("FOREIGN-KEY-TEST-OneToOne")
          )

          agent ! msg

          expectMsgType[GlobalMessages.TransformationStarted](FiniteDuration(5, SECONDS))
          expectMsgType[GlobalMessages.TransformationCompleted](FiniteDuration(7, SECONDS))

          val s = connection.createStatement()
          withClue("Written data should be correct!") {
            val expectedData = Map(
              "Einstein" -> new java.math.BigDecimal("3.14"),
              "Riemann"  -> new java.math.BigDecimal("6.28"),
              "Gauß"     -> new java.math.BigDecimal("12.56"),
              "Listing"  -> new java.math.BigDecimal("25.12"),
              "Leibnitz" -> new java.math.BigDecimal("50.24")
            )
            val r = s.executeQuery(
              "SELECT t_employees.id AS id, t_employees.lastname AS name, t_salary.amount AS amount FROM t_employees JOIN t_salary ON t_employees.id = t_salary.employee_id"
            )
            if (r.next()) {
              r.getBigDecimal("amount") should be(expectedData(r.getString("name")))

              while (r.next()) {
                r.getBigDecimal("amount") should be(expectedData(r.getString("name")))
              }
            } else
              fail("No results found in database!")
          }

          connection.close()
        }
      }
    }
  }
}
