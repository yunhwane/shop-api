rootProject.name = "shop-api"

include(
    "core:core-enum",
    "core:core-domain",
    "infrastructure:storage-db",
    "infrastructure:security",
    "infrastructure:client-mail",
    "infrastructure:client-payment-toss",
    "api",
    "tests:architecture",
    "tests:api-docs",
)
