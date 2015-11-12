package io.finch.petstore

import _root_.argonaut._, Argonaut._

import com.twitter.finagle.Service
import com.twitter.finagle.http.exp.Multipart.FileUpload
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.Future
import io.finch._
import io.finch.argonaut._
import io.finch.request._
import io.finch.request.items._

/**
 * Provides the paths and endpoints for all the API's public service methods.
 */
object endpoint {

  implicit val encodeException: EncodeJson[Exception] = EncodeJson {
    case Error.NotPresent(ParamItem(p)) => Json.obj(
      "error" -> jString("param_not_present"), "param" -> jString(p)
    )
    case Error.NotPresent(BodyItem) => Json.obj(
      "error" -> jString("body_not_present")
    )
    case Error.NotParsed(ParamItem(p), _, _) => Json.obj(
      "error" -> jString("param_not_parsed"), "param" -> jString(p)
    )
    case Error.NotParsed(BodyItem, _, _) => Json.obj(
      "error" -> jString("body_not_parsed")
    )
    case Error.NotValid(ParamItem(p), rule) => Json.obj(
      "error" -> jString("param_not_valid"), "param" -> jString(p), "rule" -> jString(rule)
    )
    // Domain errors
    case error: PetstoreError => Json.obj(
      "error" -> jString(error.message)
    )
  }

  /**
   * Private method that compiles all pet service endpoints.
   * @return Bundled compilation of all pet service endpoints.
   */
  private def pets(db: PetstoreDb) =
    getPet(db) :+:
    addPet(db) :+:
    updatePet(db) :+:
    getPetsByStatus(db) :+:
    findPetsByTag(db) :+:
    deletePet(db) :+:
    updatePetViaForm(db) :+:
    uploadImage(db)

  /**
   * Private method that compiles all store service endpoints.
   * @return Bundled compilation of all store service endpoints.
   */
  private def store(db: PetstoreDb) =
    getInventory(db) :+:
    addOrder(db) :+:
    deleteOrder(db) :+:
    findOrder(db)

  /**
   * Private method that compiles all user service endpoints.
   * @return Bundled compilation of all user service endpoints.
   */
  private def users(db: PetstoreDb) =
    addUser(db) :+:
    addUsersViaList(db) :+:
    addUsersViaArray(db) :+:
    getUser(db) :+:
    deleteUser(db) :+:
    updateUser(db)

  /**
   * Compiles together all the endpoints relating to public service methods.
   * @return A service that contains all provided endpoints of the API.
   */
  def makeService(db: PetstoreDb): Service[Request, Response] = (
    pets(db) :+:
    store(db) :+:
    users(db)
  ).handle({
    case e: PetstoreError => NotFound(e)
  }).toService

  /**
   * The long passed in the path becomes the ID of the Pet fetched.
   * @return A Router that contains the Pet fetched.
   */
  def getPet(db: PetstoreDb): Endpoint[Pet] =
    get("pet" / long) { id: Long => Ok(db.getPet(id)) }

  /**
   * The pet to be added must be passed in the body.
   * @return A Router that contains a RequestReader of the ID of the Pet added.
   */
  def addPet(db: PetstoreDb): Endpoint[Long] =
    post("pet" ? body.as[Pet]) { p: Pet =>
      Ok(db.addPet(p))
    }

  /**
   * The updated, better version of the current pet must be passed in the body.
   * @return A Router that contains a RequestReader of the updated Pet.
   */
  def updatePet(db: PetstoreDb): Endpoint[Pet] =
    put("pet" ? body.as[Pet]) { pet: Pet =>
      val identifier: Long = pet.id match {
        case Some(num) => num
        case None => throw MissingIdentifier("The updated pet must have a valid id.")
      }

      Ok(db.updatePet(pet.copy(id = Some(identifier))))
    }

  /**
   * The status is passed as a query parameter.
   * @return A Router that contains a RequestReader of the sequence of all Pets with the Status in question.
   */
  def getPetsByStatus(db: PetstoreDb): Endpoint[Seq[Pet]] =
    get("pet" / "findByStatus" ? reader.findByStatusReader) { s: Seq[String] =>
      Ok(db.getPetsByStatus(s))
    }

  /**
   * The tags are passed as query parameters.
   * @return A Router that contains a RequestReader of the sequence of all Pets with the given Tags.
   */
  def findPetsByTag(db: PetstoreDb): Endpoint[Seq[Pet]] =
    get("pet" / "findByTags" ? reader.tagReader) { s: Seq[String] =>
      Ok(db.findPetsByTag(s))
    }

  /**
   * The ID of the pet to delete is passed in the path.
   * @return A Router that contains a RequestReader of the deletePet result (true for success, false otherwise).
   */
  def deletePet(db: PetstoreDb): Endpoint[Unit] =
    delete("pet" / long) { petId: Long =>
      NoContent(db.deletePet(petId))
    }

  /**
   * Endpoint for the updatePetViaForm (form data) service method. The pet's ID is passed in the path.
   * @return A Router that contains a RequestReader of the Pet that was updated.
   */
  def updatePetViaForm(db: PetstoreDb): Endpoint[Pet] =
    post("pet" / long ? reader.nameReader ? reader.statusReader) { (petId: Long, n: String, s: Status) =>
      for {
        pet <- db.getPet(petId)
        newPet <- db.updatePetNameStatus(petId, Some(n), Some(s))
      } yield Ok(newPet)
    }

  /**
   * The ID of the pet corresponding to the image is passed in the path, whereas the image
   * file is passed as form data.
   * @return A Router that contains a RequestReader of the uploaded image's url.
   */
  def uploadImage(db: PetstoreDb): Endpoint[String] =
    post("pet" / long / "uploadImage" ? fileUpload("file")) { (id: Long, upload: FileUpload) =>
      Ok(db.addImage(id, upload.content))
    }

  /**
   * @return A Router that contains a RequestReader of a Map reflecting the inventory.
   */
  def getInventory(db: PetstoreDb): Endpoint[Inventory] =
    get("store" / "inventory") { Ok(db.getInventory) }

  /**
   * The order to be added is passed in the body.
   * @return A Router that contains a RequestReader of the autogenerated ID for the added Order.
   */
  def addOrder(db: PetstoreDb): Endpoint[Long] =
    post("store" / "order" ? body.as[Order]) { o: Order =>
      Ok(db.addOrder(o))
    }

  /**
   * The ID of the order to be deleted is passed in the path.
   * @return A Router that contains a RequestReader of result of the deleteOrder method (true for success, false else).
   */
  def deleteOrder(db: PetstoreDb): Endpoint[Boolean] =
    delete("store" / "order" / long) { id: Long =>
      Ok(db.deleteOrder(id))
    }

  /**
   * The ID of the order to be found is passed in the path.
   * @return A Router that contains a RequestReader of the Order in question.
   */
  def findOrder(db: PetstoreDb): Endpoint[Order] =
    get("store" / "order" / long) { id: Long =>
      Ok(db.findOrder(id))
    }

  /**
   * The information of the added User is passed in the body.
   * @return A Router that contains a RequestReader of the username of the added User.
   */
  def addUser(db: PetstoreDb): Endpoint[String] =
    post("user" ? body.as[User]) { u: User =>
      Ok(db.addUser(u))
    }

  /**
   * The list of Users is passed in the body.
   * @return A Router that contains a RequestReader of a sequence of the usernames of the Users added.
   */
  def addUsersViaList(db: PetstoreDb): Endpoint[Seq[String]] =
    post("user" / "createWithList" ? body.as[Seq[User]]) { s: Seq[User] =>
      Ok(Future.collect(s.map(db.addUser)))
    }

  /**
   * The array of users is passed in the body.
   * @return A Router that contains a RequestReader of a sequence of the usernames of the Users added.
   */
  def addUsersViaArray(db: PetstoreDb): Endpoint[Seq[String]] =
    post("user" / "createWithArray" ? body.as[Seq[User]]) { s: Seq[User] =>
      Ok(Future.collect(s.map(db.addUser)))
    }

  /**
   * The username of the User to be deleted is passed in the path.
   * @return A Router that contains essentially nothing unless an error is thrown.
   */
  def deleteUser(db: PetstoreDb): Endpoint[Unit] =
    delete("user" / string) { n: String =>
      Ok(db.deleteUser(n))
    }

  /**
   * The username of the User to be found is passed in the path.
   * @return A Router that contains the User in question.
   */
  def getUser(db: PetstoreDb): Endpoint[User] =
    get("user" / string) { n: String =>
      Ok(db.getUser(n))
    }

  /**
   * The username of the User to be updated is passed in the path.
   * @return A Router that contains a RequestReader of the User updated.
   */
  def updateUser(db: PetstoreDb): Endpoint[User] =
    put("user" / string ? body.as[User]) { (n: String, u: User) =>
      Ok(db.updateUser(u))
    }
}

