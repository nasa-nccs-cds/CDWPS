package servers

class APIManager(val providers: Map[String, ServiceProvider], default_service: String) {

  def getServiceProvider(service: String = ""): Option[ServiceProvider] = {
    def actual_service = if (service == "") default_service else service
    providers.get(actual_service)
  }
}

object APIManager {

  def apply(): APIManager = {
    import ServiceProviderConfiguration._
    new APIManager(providers.toMap, default_service)
  }
}

