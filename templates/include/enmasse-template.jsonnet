local storage = import "storage-template.jsonnet";
local common = import "common.jsonnet";
local enmasseInfra = import "enmasse-instance-infra.jsonnet";
local addressController = import "address-controller.jsonnet";
local authService = import "auth-service.jsonnet";
local restapiRoute = import "restapi-route.jsonnet";
local images = import "images.jsonnet";
{
  generate(with_kafka)::
  {
    "apiVersion": "v1",
    "kind": "Template",
    "metadata": {
      "labels": {
        "app": "enmasse"
      },
      "name": "enmasse"
    },
    "objects": [ storage.template(false, false),
                 storage.template(false, true),
                 storage.template(true, false),
                 storage.template(true, true),
                 enmasseInfra.generate(with_kafka),
                 authService.none_deployment("${NONE_AUTHSERVICE_IMAGE}", "${NONE_AUTHSERVICE_CERT_SECRET_NAME}"),
                 authService.none_authservice,
                 addressController.deployment("${ADDRESS_CONTROLLER_REPO}", "${MULTIINSTANCE}", "", "${ENMASSE_CA_SECRET}", "${ADDRESS_CONTROLLER_CERT_SECRET}", "${ADDRESS_CONTROLLER_ENABLE_API_AUTH}"),
                 common.empty_secret("address-controller-userdb"),
                 addressController.internal_service,
                 restapiRoute.route("${RESTAPI_HOSTNAME}") ],
    "parameters": [
      {
        "name": "RESTAPI_HOSTNAME",
        "description": "The hostname to use for the exposed route for the REST API"
      },
      {
        "name": "MULTIINSTANCE",
        "description": "If set to true, the address controller will deploy infrastructure to separate namespaces",
        "value": "false"
      },
      {
        "name": "ADDRESS_CONTROLLER_REPO",
        "description": "The docker image to use for the address controller",
        "value": images.address_controller
      },
      {
        "name": "NONE_AUTHSERVICE_IMAGE",
        "description": "The docker image to use for the 'none' auth service",
        "value": images.none_authservice
      },
      {
        "name": "ENMASSE_CA_SECRET",
        "description": "Name of the secret containing the EnMasse CA",
        "value": "enmasse-ca"
      },
      {
        "name": "ADDRESS_CONTROLLER_CERT_SECRET",
        "description": "Name of the secret containing the address controller certificate",
        "value": "address-controller-cert"
      },
      {
        "name": "NONE_AUTHSERVICE_CERT_SECRET_NAME",
        "description": "The secret to use for the none-authservice certificate",
        "value": "none-authservice-cert"
      },
      {
        "name": "ADDRESS_CONTROLLER_ENABLE_API_AUTH",
        "description": "Enable/disable user authentication for API",
        "value": "false"
      },
    ]
  }
}
