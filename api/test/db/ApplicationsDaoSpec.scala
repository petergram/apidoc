package db

import lib.Role
import com.bryzek.apidoc.api.v0.models.{Application, ApplicationForm, Organization, OriginalType, Visibility}
import org.scalatest.{FunSpec, Matchers}
import org.junit.Assert._
import java.util.UUID
import play.api.libs.json.Json

class ApplicationsDaoSpec extends FunSpec with Matchers {

  new play.core.StaticApplication(new java.io.File("."))

  private lazy val baseUrl = "http://localhost"

  private[this] val Original = com.bryzek.apidoc.api.v0.models.Original(
    `type` = OriginalType.ApiJson,
    data = Json.obj("name" -> s"test-${UUID.randomUUID}").toString
  )

  private[this] def upsertApplication(
    nameOption: Option[String] = None,
    org: Organization = Util.testOrg,
    visibility: Visibility = Visibility.Organization
  ): Application = {
    val n = nameOption.getOrElse("Test %s".format(UUID.randomUUID))
    ApplicationsDao.findAll(Authorization.All, orgKey = Some(org.key), name = Some(n), limit = 1).headOption.getOrElse {
      val applicationForm = ApplicationForm(
        name = n,
        description = None,
        visibility = visibility
      )
      ApplicationsDao.create(Util.createdBy, org, applicationForm)
    }
  }

  private[this] def findByKey(org: Organization, key: String): Option[Application] = {
    ApplicationsDao.findAll(Authorization.All, orgKey = Some(org.key), key = Some(key), limit = 1).headOption
  }

  it("create") {
    val name = "Test %s".format(UUID.randomUUID)
    val application = upsertApplication(Some(name))
    application.name should be(name)
  }

  describe("canUserUpdate") {

    it("for an org I belong to") {
      val user = Util.createRandomUser()
      val org = Util.createOrganization(user)
      val app = Util.createApplication(org)
      ApplicationsDao.canUserUpdate(user, app) should be(true)
    }

    it("for an org I do NOT belong to") {
      val app = Util.createApplication()
      ApplicationsDao.canUserUpdate(Util.createRandomUser(), app) should be(false)
    }

  }

  describe("validate") {

    def createForm() = ApplicationForm(
      name = "Test %s".format(UUID.randomUUID),
      key = None,
      description = None,
      visibility = Visibility.Organization
    )

    it("returns empty if valid") {
      ApplicationsDao.validate(Util.testOrg, createForm(), None) should be(Seq.empty)
    }

    it("returns error if name already exists") {
      val form = createForm()
      ApplicationsDao.create(Util.createdBy, Util.testOrg, form)
      ApplicationsDao.validate(Util.testOrg, form, None).map(_.code) should be(Seq("validation_error"))
    }

    it("returns empty if name exists but belongs to the application we are updating") {
      val form = createForm()
      val application = ApplicationsDao.create(Util.createdBy, Util.testOrg, form)
      ApplicationsDao.validate(Util.testOrg, form, Some(application)) should be(Seq.empty)
    }

    it("key") {
      val form = createForm()
      val application = ApplicationsDao.create(Util.createdBy, Util.testOrg, form)

      val newForm = form.copy(name = application.name + "2", key = Some(application.key))
      ApplicationsDao.validate(Util.testOrg, newForm, None).map(_.message) should be(Seq("Application with this key already exists"))
      ApplicationsDao.validate(Util.testOrg, newForm, Some(application)) should be(Seq.empty)
    }

  }

  describe("update") {

    def toForm(app: Application): ApplicationForm = {
      ApplicationForm(
        name = app.name,
        key = Some(app.key),
        description = app.description,
        visibility = app.visibility
      )
    }

    it("name") {
      val name = "Test %s".format(UUID.randomUUID)
      val application = upsertApplication(Some(name))
      val newName = application.name + "2"
      ApplicationsDao.update(Util.createdBy, application, toForm(application).copy(name = newName))
      findByKey(Util.testOrg, application.key).get.name should be(newName)
    }

    it("description") {
      val application = upsertApplication()
      val newDescription = "Test %s".format(UUID.randomUUID)
      findByKey(Util.testOrg, application.key).get.description should be(None)
      ApplicationsDao.update(Util.createdBy, application, toForm(application).copy(description = Some(newDescription)))
      findByKey(Util.testOrg, application.key).get.description should be(Some(newDescription))
    }

    it("visibility") {
      val application = upsertApplication()
      application.visibility should be(Visibility.Organization)

      ApplicationsDao.update(Util.createdBy, application, toForm(application).copy(visibility = Visibility.Public))
      findByKey(Util.testOrg, application.key).get.visibility should be(Visibility.Public)

      ApplicationsDao.update(Util.createdBy, application, toForm(application).copy(visibility = Visibility.Organization))
      findByKey(Util.testOrg, application.key).get.visibility should be(Visibility.Organization)
    }
  }

  describe("findAll") {

    val user = Util.createRandomUser()
    val org = Util.createOrganization(user, Some("Public " + UUID.randomUUID().toString))
    val publicApplication = ApplicationsDao.create(user, org, ApplicationForm(name = "svc-public", visibility = Visibility.Public))
    val privateApplication = ApplicationsDao.create(user, org, ApplicationForm(name = "svc-private", visibility = Visibility.Organization))

    it("by orgKey") {
      val guids = ApplicationsDao.findAll(Authorization.All, orgKey = Some(org.key)).map(_.guid)
      guids.contains(publicApplication.guid) should be(true)
      guids.contains(privateApplication.guid) should be(true)
    }

    it("by guid") {
      val guids = ApplicationsDao.findAll(Authorization.All, orgKey = Some(org.key), guid = Some(publicApplication.guid)).map(_.guid)
      guids.contains(publicApplication.guid) should be(true)
      guids.contains(privateApplication.guid) should be(false)
    }

    it("by key") {
      val guids = ApplicationsDao.findAll(Authorization.All, orgKey = Some(org.key), key = Some(publicApplication.key)).map(_.guid)
      guids.contains(publicApplication.guid) should be(true)
      guids.contains(privateApplication.guid) should be(false)
    }

    it("by name") {
      val guids = ApplicationsDao.findAll(Authorization.All, orgKey = Some(org.key), name = Some(publicApplication.name)).map(_.guid)
      guids.contains(publicApplication.guid) should be(true)
      guids.contains(privateApplication.guid) should be(false)
    }

    it("hasVersion") {
      val app = Util.createApplication(org)

      ApplicationsDao.findAll(
        Authorization.All,
        guid = Some(app.guid),
        hasVersion = Some(false)
      ).map(_.guid) should be(Seq(app.guid))

      ApplicationsDao.findAll(
        Authorization.All,
        guid = Some(app.guid),
        hasVersion = Some(true)
      ).map(_.guid) should be(Seq.empty)

      val service = Util.createService(app)
      val version = VersionsDao.create(Util.createdBy, app, "1.0.0", Original, service)

      ApplicationsDao.findAll(
        Authorization.All,
        guid = Some(app.guid),
        hasVersion = Some(false)
      ).map(_.guid) should be(Seq.empty)

      ApplicationsDao.findAll(
        Authorization.All,
        guid = Some(app.guid),
        hasVersion = Some(true)
      ).map(_.guid) should be(Seq(app.guid))

    }

    describe("Authorization") {

      describe("All") {

        it("sees both applications") {
          val guids = ApplicationsDao.findAll(Authorization.All, orgKey = Some(org.key)).map(_.guid)
          guids.contains(publicApplication.guid) should be(true)
          guids.contains(privateApplication.guid) should be(true)
        }

      }

      describe("PublicOnly") {

        it("sees only the public application") {
          val guids = ApplicationsDao.findAll(Authorization.PublicOnly, orgKey = Some(org.key)).map(_.guid)
          guids.contains(publicApplication.guid) should be(true)
          guids.contains(privateApplication.guid) should be(false)
        }

      }

      describe("User") {

        it("user can see own application") {
          val guids = ApplicationsDao.findAll(Authorization.User(user.guid), orgKey = Some(org.key)).map(_.guid)
          guids.contains(publicApplication.guid) should be(true)
          guids.contains(privateApplication.guid) should be(true)
        }

        it("other user cannot see public nor private applications for a private org") {
          val guids = ApplicationsDao.findAll(Authorization.User(Util.createdBy.guid), orgKey = Some(org.key)).map(_.guid)
          guids.contains(publicApplication.guid) should be(false)
          guids.contains(privateApplication.guid) should be(false)
        }

        it("other user cannot see private application even for a public org") {
          val myUser = Util.createRandomUser()
          val myOrg = Util.createOrganization(visibility = Visibility.Public)
          Util.createMembership(myOrg, myUser, Role.Member)
          val myPrivateApp = upsertApplication(org = myOrg)
          val myPublicApp = upsertApplication(org = myOrg, visibility = Visibility.Public)

          val otherUser = Util.createRandomUser()
          val otherOrg = Util.createOrganization(visibility = Visibility.Public)
          Util.createMembership(otherOrg, otherUser, Role.Member)
          val otherPrivateApp = upsertApplication(org = otherOrg)
          val otherPublicApp = upsertApplication(org = otherOrg, visibility = Visibility.Public)

          val myGuids = ApplicationsDao.findAll(Authorization.User(myUser.guid), orgKey = Some(myOrg.key)).map(_.guid)
          myGuids.contains(myPrivateApp.guid) should be(true)
          myGuids.contains(myPublicApp.guid) should be(true)

          val otherGuids = ApplicationsDao.findAll(Authorization.User(myUser.guid), orgKey = Some(otherOrg.key)).map(_.guid)
          otherGuids.contains(otherPrivateApp.guid) should be(false)
          otherGuids.contains(otherPublicApp.guid) should be(true)
        }
      }

    }

  }

}
