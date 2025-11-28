package com.example.cdk.azure

import com.hashicorp.cdktf.*
import com.hashicorp.cdktf.providers.azurerm.application_insights.ApplicationInsights
import com.hashicorp.cdktf.providers.azurerm.application_insights.ApplicationInsightsConfig
import com.hashicorp.cdktf.providers.azurerm.data_azurerm_resource_group.DataAzurermResourceGroup
import com.hashicorp.cdktf.providers.azurerm.data_azurerm_resource_group.DataAzurermResourceGroupConfig
import com.hashicorp.cdktf.providers.azurerm.linux_function_app.*
import com.hashicorp.cdktf.providers.azurerm.log_analytics_workspace.LogAnalyticsWorkspace
import com.hashicorp.cdktf.providers.azurerm.log_analytics_workspace.LogAnalyticsWorkspaceConfig
import com.hashicorp.cdktf.providers.azurerm.provider.AzurermProvider
import com.hashicorp.cdktf.providers.azurerm.provider.AzurermProviderConfig
import com.hashicorp.cdktf.providers.azurerm.provider.AzurermProviderFeatures
import com.hashicorp.cdktf.providers.azurerm.role_assignment.RoleAssignment
import com.hashicorp.cdktf.providers.azurerm.role_assignment.RoleAssignmentConfig
import com.hashicorp.cdktf.providers.azurerm.service_plan.ServicePlan
import com.hashicorp.cdktf.providers.azurerm.service_plan.ServicePlanConfig
import com.hashicorp.cdktf.providers.azurerm.storage_account.StorageAccount
import com.hashicorp.cdktf.providers.azurerm.storage_account.StorageAccountConfig
import com.hashicorp.cdktf.providers.azurerm.storage_container.StorageContainer
import com.hashicorp.cdktf.providers.azurerm.storage_container.StorageContainerConfig
import software.constructs.Construct


class AzureStack(scope: Construct, id: String) :
    TerraformStack(scope, id) {

    init {
        val azureClientIdVar = TerraformVariable(
            this,
            "AZURE_CLIENT_ID",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Azure client ID")
                .build()
        )

        val azureClientSecretVar = TerraformVariable(
            this,
            "AZURE_CLIENT_SECRET",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Azure client secret")
                .build()
        )

        val azureSubscriptionIdVar = TerraformVariable(
            this,
            "AZURE_SUBSCRIPTION_ID",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Azure subscription ID")
                .build()
        )

        val azureTenantIdVar = TerraformVariable(
            this,
            "AZURE_TENANT_ID",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Azure tenant ID")
                .build()
        )

        val azureStorageAccountNameVar = TerraformVariable(
            this,
            "AZURE_STORAGE_ACCOUNT_NAME",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Azure storage account name")
                .build()
        )

        val azureResourceGroupNameVar = TerraformVariable(
            this,
            "AZURE_RESOURCE_GROUP_NAME",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Azure resource group name")
                .build()
        )

        val resourceGroupName = azureResourceGroupNameVar.stringValue
        val functionAppName =
            "demo-spring-clean-architecture-fun"
        val appServicePlanName =
            "demo_serverless_app_plan"


        // Configure the Azure Provider
        AzurermProvider(
            this,
            "Azure",
            AzurermProviderConfig.builder()
                .subscriptionId(azureSubscriptionIdVar.stringValue)
                .clientId(azureClientIdVar.stringValue)
                .clientSecret(azureClientSecretVar.stringValue)
                .tenantId(azureTenantIdVar.stringValue)
                .features(
                    mutableListOf(
                        AzurermProviderFeatures.builder().build()
                    )
                )
                .build()
        )

        // Configure Terraform Backend to Use Azure Blob Storage
        AzurermBackend(
            this,
            AzurermBackendConfig.builder()
                .resourceGroupName("\${resource_group_name}")
                .storageAccountName("\${storage_account_name}")
                .containerName("vintikterraformstorage")
                .key("demo-kscfunction/terraform.tfstate")
                .clientId("\${client_id}")
                .clientSecret("\${client_secret}")
                .subscriptionId("\${subscription_id}")
                .tenantId("\${tenant_id}")
                .build()
        )
        // Reference the existing Resource Group
        val resourceGroup = DataAzurermResourceGroup(
            this,
            "ExistingResourceGroup",
            DataAzurermResourceGroupConfig.builder()
                .name(resourceGroupName) // Use existing resource group name
                .build()
        )

        // Create a new Log Analytics Workspace
        val logAnalyticsWorkspace = LogAnalyticsWorkspace(
            this, "DemoLogAnalyticsWorkspace",
            LogAnalyticsWorkspaceConfig.builder()
                .name("demo-mocknest-loganalytics")  // choose your preferred name
                .resourceGroupName(resourceGroup.name)
                .location(resourceGroup.location)
                .sku("PerGB2018") // recommended default SKU
                .retentionInDays(30) // example retention
                .build()
        )

        // Create Storage Account for Blob Storage
        val storageAccountMockNest = StorageAccount(
            this,
            "demo-mocknest-sa",
            StorageAccountConfig.builder()
                .name("demomocknest")  // ✅ Storage account name
                .resourceGroupName(resourceGroup.name)
                .location(resourceGroup.location)
                .accountTier("Standard")
                .accountReplicationType("LRS")
                .build()
        )

        // Create a Blob Storage Container for MockNest Mappings
        val storageContainer = StorageContainer(
            this,
            "demo-mocknest-container",
            StorageContainerConfig.builder()
                .name("demo-mocknest-mappings")  // Blob storage container name
                .storageAccountName(storageAccountMockNest.name)
                .containerAccessType("private")  // Private access for security
                .dependsOn(listOf(storageAccountMockNest))
                .build()
        )

        // Create an App Service Plan
        val servicePlan = ServicePlan(
            this, "DemoSpringCloudExampleServicePlan",
            ServicePlanConfig.builder()
                .dependsOn(listOf(resourceGroup))
                .name(appServicePlanName)
                .resourceGroupName(resourceGroup.name)
                .osType("Linux")
                .skuName("Y1")
                .location(resourceGroup.location)
                .build()
        )

        // Create an Application Insights resource
        val appInsights = ApplicationInsights(
            this, "AppInsights",
            ApplicationInsightsConfig.builder()
                .name("demo-spring-cloud-app-insights")
                .resourceGroupName(resourceGroup.name)
                .location(resourceGroup.location)
                .applicationType("java")
                .workspaceId(logAnalyticsWorkspace.id)
                .build()
        )
        val storageAccountAccessKeyVar = TerraformVariable(
            this,
            "AZURE_STORAGE_ACCOUNT_ACCESS_KEY",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Storage account access key")
                .build()
        )

        val storageAccountAccessKey = storageAccountAccessKeyVar.stringValue

        // Create the Function App
        val functionApp = LinuxFunctionApp(
            this, "DemoSpringCloudFunctionApp",
            LinuxFunctionAppConfig.builder()
                .dependsOn(
                    listOf(
                        resourceGroup,
                        servicePlan,
                        appInsights,
                    )
                )
                .name(functionAppName)
                .resourceGroupName(resourceGroup.name)
                .location(resourceGroup.location)
                .servicePlanId(servicePlan.id)
                .storageAccountName(azureStorageAccountNameVar.stringValue)
                .storageAccountAccessKey(
                    storageAccountAccessKey
                )
                .siteConfig(
                    LinuxFunctionAppSiteConfig.builder()
                        .applicationStack(
                            LinuxFunctionAppSiteConfigApplicationStack.builder()
                                .javaVersion("21")
                                .build()
                        ).build()
                )
                .identity(
                    LinuxFunctionAppIdentity.builder()
                        .type("SystemAssigned")
                        .build() // ✅ Enables Managed Identity
                )
                .appSettings(
                    mapOf(
                        "MAIN_CLASS" to "com.example.clean.architecture.Application",
                        "APPINSIGHTS_INSTRUMENTATIONKEY" to appInsights.instrumentationKey,
                        "APPLICATIONINSIGHTS_ENABLE_AGENT" to "true",
                        "APPLICATIONINSIGHTS_INSTRUMENTATION_LOGGING_LEVEL" to "INFO",
                        "APPLICATIONINSIGHTS_SELF_DIAGNOSTICS_LEVEL" to "ERROR",
                        "WEBSITE_RUN_FROM_PACKAGE" to "1",
                    )
                )
                .build()
        )
        // Assign Function App Storage Permissions (Read/Write)
        val functionAppBlobStorageRole = RoleAssignment(
            this,
            "DemoFunctionAppBlobStorageRole",
            RoleAssignmentConfig.builder()
                .scope(storageAccountMockNest.id)  // Assign access at the Storage Account level
                .roleDefinitionName("Storage Blob Data Contributor")  // Allows reading and writing blobs
                .principalId(functionApp.identity.principalId)  // Assign to Function App's Managed Identity
                .build()
        )

        val functionAppStorageContributorRole = RoleAssignment(
            this,
            "DemoFunctionAppStorageContributorRole",
            RoleAssignmentConfig.builder()
                .scope(storageAccountMockNest.id) // ✅ Give access to full Storage Account management
                .roleDefinitionName("Storage Account Contributor") // ✅ Allows creating/deleting tables
                .principalId(functionApp.identity.principalId) // ✅ Assign to Function App's Managed Identity
                .build()
        )

    }
}
