package controllers

import play.api.mvc._

object AccountController extends Controller {

  implicit val context = scala.concurrent.ExecutionContext.Implicits.global

  def redirect = Action { implicit request =>
    Redirect(routes.AccountController.index())
  }

  def index() = Authenticated { implicit request =>
    Redirect(routes.AccountProfileController.index())
  }

}
