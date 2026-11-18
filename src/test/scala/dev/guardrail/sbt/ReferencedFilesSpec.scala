package dev.guardrail.sbt

import _root_.sbt.IO

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ReferencedFilesSpec extends AnyFunSuite with Matchers {

  test("Follows relative $refs transitively and ignores local and remote refs") {
    IO.withTemporaryDirectory { dir =>
      val spec = new java.io.File(dir, "apiv1.yaml")
      val responses = new java.io.File(dir, "components/responses.yaml")
      val schemas = new java.io.File(dir, "components/schemas/error.json")
      IO.write(
        spec,
        """openapi: 3.0.0
          |paths:
          |  /foo:
          |    get:
          |      responses:
          |        '400':
          |          $ref: 'components/responses.yaml#/BadRequest'
          |        '404':
          |          $ref: '#/components/responses/NotFound'
          |        '500':
          |          $ref: 'https://example.com/responses.yaml#/Error'
          |""".stripMargin
      )
      IO.write(
        responses,
        """BadRequest:
          |  content:
          |    application/json:
          |      schema:
          |        $ref: './schemas/error.json'
          |Missing:
          |  $ref: 'missing.yaml'
          |""".stripMargin
      )
      IO.write(schemas, """{"type": "object", "properties": {"self": {"$ref": "../responses.yaml#/BadRequest"}}}""")

      GuardrailHelpers.referencedFiles(spec) shouldBe Set(responses.getCanonicalFile, schemas.getCanonicalFile)
    }
  }

  test("A spec without external refs has no referenced files") {
    IO.withTemporaryDirectory { dir =>
      val spec = new java.io.File(dir, "apiv1.yaml")
      IO.write(spec, "openapi: 3.0.0\npaths: {}\n")
      GuardrailHelpers.referencedFiles(spec) shouldBe empty
    }
  }
}
