/*
* SchluterDitraHeat
*
* Description:
* This Hubitat driver is designed for use with a Schluter DITRA-HEAT Wifi thermostat
*
* Preferences:
* Email Address = REQUIRED. The e-mail address for the Schluter account
* Password = REQUIRED. Login password for the Schluter account
* RefreshRate = REQUIRED - DEFAULT = 5 minutes. The rate at which the thermostats will be polled
* DebugLogs = OPTIONAL - DEFAULT = false. Should debug logging be enabled?
*
* Features List:
*   View data from Schluter DITRA-HEAT Wifi thermostats
* 
* Licensing:
* Copyright 2023 Marc Reyhner
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
* in compliance with the License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
* for the specific language governing permissions and limitations under the License.
*
* Version Control:
* 0.1.0 - Initial version
* 
* Thank you(s):
*   I used the Tesla PowerWall driver fron snell as a sample and skeleton for creating a HTTP based driver
*       https://community.hubitat.com/t/project-driver-for-connecting-to-your-tesla-devices/41375
*   API details learned from py-schluter library - https://github.com/prairieapps/py-schluter/
*/

import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime
import groovy.transform.Field
import java.time.LocalDate

@Field static final String baseUri = "https://ditra-heat-e-wifi.schluter.com/"


metadata{
    definition ( name: "SchluterDitraHeat", namespace: "marcre", author: "Marc Reyhner", importUrl: "tbd" ) {
        // Attempting to indicate what capabilities the device should be capable of
        capability "Refresh"

        // Keep track of connection status
        attribute "Status", "string"
    }
    preferences{
        section{
            // String to retain the device's IP or hostname
            input( type: "string", name: "EmailAddress", title: "<font color='FF0000'><b>Schulter account e-mail address</b></font>", required: true )
            // String to retain the customer's login password
            input( type: "password", name: "Password", title: "<font color='FF0000'><b>Schulter account password</b></font>", required: true )
            // Enum to allow selecting the refresh rate that the device will be checked
            input( type: "enum", name: "RefreshRate", title: "<b>Refresh Rate</b>", required: false, multiple: false, options: [ "5 seconds", "10 seconds", "15 seconds", "30 seconds", "1 minute", "5 minutes", "10 minutes", "15 minutes", "30 minutes", "1 hour", "3 hours", "Manual" ], defaultValue: "5 minutes" )
            // Enum to set the level of logging that will be used
            input( type: "bool", name: "DebugLogs", title: "<b>Enable Debug Logging?</b>", required: true, multiple: false, defaultValue: false )
        }
    }
}

// uninstalling device so make sure to clean up children
void uninstalled() {
	// Delete all children
	getChildDevices().each{
		deleteChildDevice( it.deviceNetworkId )
	}
    log.info("Uninstalled")
}

// updated is called whenever device parameters are saved
// Sets the current version of the driver, basic settings, and schedules
def updated() {

    // On update clear out any old session id in case login information changed
    state.remove("SessionId")

    // Check if the refresh rate is not set for some reason and putting it at the default
    if( RefreshRate == null ) {
        RefreshRate = "5 minutes"
    }
    // Set the schedule for driver version check and refreshing for data
    def Hour = ( new Date().format( "h" ) as int )
    def Minute = ( new Date().format( "m" ) as int )
    def Second = ( new Date().format( "s" ) as int )
    Second = ( (Second + 5) % 60 )

    // Check what the refresh rate is set for then run it
    switch( RefreshRate ){
        case "5 seconds": // Schedule the refresh check for every 5 seconds
            schedule( "0/5 * * ? * *", "refresh" )
            break
        case "10 seconds": // Schedule the refresh check for every 10 seconds
            schedule( "0/10 * * ? * *", "refresh" )
            break
        case "15 seconds": // Schedule the refresh check for every 15 seconds
            schedule( "0/15 * * ? * *", "refresh" )
            break
        case "30 seconds": // Schedule the refresh check for every 30 seconds
            schedule( "0/30 * * ? * *", "refresh" )
            break
        case "1 minute": // Schedule the refresh check for every minute
            schedule( "${ Second } * * ? * *", "refresh" )
            break
        case "5 minutes": // Schedule the refresh check for every 5 minutes
            schedule( "${ Second } 0/5 * ? * *", "refresh" )
            break
        case "10 minutes": // Schedule the refresh check for every 10 minutes
            schedule( "${ Second } 0/10 * ? * *", "refresh" )
            break
        case "15 minutes": // Schedule the refresh check for every 15 minutes
            schedule( "${ Second } 0/15 * ? * *", "refresh" )
            break
        case "30 minutes": // Schedule the refresh check for every 30 minutes
            schedule( "${ Second } 0/30 * ? * *", "refresh" )
            break
        case "1 hour": // Schedule the refresh check for every hour
            schedule( "${ Second } ${ Minute } * ? * *", "refresh" )
            break
        case "3 hours": // Schedule the refresh check for every 3 hours
            schedule( "${ Second } ${ Minute } 0/3 ? * *", "refresh" )
            break
        default:
            unschedule( "refresh" )
            RefreshRate = "Manual"
            break
    }
    log.info( "Refresh rate: ${ RefreshRate }" )

    log.info("Updated")
}

def CanProceed() {
    if( EmailAddress == null || Password == null){
        UpsertAttribute( "Status", "Unsuccessful: Lacking account email or password" )
        log.error( "Cannot update thermostats without username or password" )

        return false
    }

    return true
}

// refresh runs the device polling
def refresh() {

    if (!CanProceed()) {
        return
    }

    try {

        LoginIfSessionExpired()

        httpGet([ uri: "${baseUri}/api/thermostats?sessionId=${GetCurrentSessionId()}" ]) {
            resp -> ProcessGetThermostatsResponse(resp)
        }
    }
    catch (IOException e)
    {
        log.error( "Error connecting to API for status. ${e}" )
        UpsertAttribute( "Status", "Local Connection Failed: ${e.message}" ) 
    }
}

def LoginIfSessionExpired() {

    if (GetCurrentSessionId()) {
        if (DebugLogsEnabled()) log.trace("Valid session, no need to request new")

        return
    }

    body = [
        Email: EmailAddress,
        Password: Password,
        Application: 7 // No idea why 7 - That's what the python driver set.  Maybe it's lucky?
    ]

    httpPostJson([ uri: "${baseUri}/api/authenticate/user", body: body]) {
        resp -> ProcessLoginResponse(resp)   
    }
}

def SetThermostatTemperature(serialNumber, temperature) {
    log.info("Received request to update temperature to ${temperature} for thermostat ${serialNumber}")

    if (!CanProceed()) {
        return
    }

    try {

        LoginIfSessionExpired()

        body = [
            ManualTemperature: temperature,
            RegulationMode: 3,
            VacationEnabled: false
        ]

        httpPostJson([ uri: "${baseUri}/api/thermostat?sessionId=${GetCurrentSessionId()}&serialnumber=${serialNumber}", body: body ]) {
            resp -> ProcessUpdateThermostatResponse(resp)
        }
    }
    catch (IOException e)
    {
        log.error( "Error connecting to API for status. ${e}" )
        UpsertAttribute( "Status", "Local Connection Failed: ${e.message}" ) 
    }
}

def SetThermostatVacationMode(String serialNumber, LocalDate startDate, LocalDate endDate, int temperature) {
    log.info("Received request to set vacation mode for thermostat ${serialNumber} from ${startDate} to ${endDate} at ${temperature}")

    if (!CanProceed()) {
        return
    }

    try {

        LoginIfSessionExpired()

        def formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

        body = [
            RegulationMode: 4,
            VacationBeginDay: startDate.atStartOfDay().format(formatter),
            VacationEnabled: true,
            VacationEndDay: endDate.atStartOfDay().format(formatter),
            VacationTemperature: temperature
        ]

        httpPostJson([ uri: "${baseUri}/api/thermostat?sessionId=${GetCurrentSessionId()}&serialnumber=${serialNumber}", body: body ]) {
            resp -> ProcessUpdateThermostatResponse(resp)
        }
    }
    catch (IOException e)
    {
        log.error( "Error connecting to API for status. ${e}" )
        UpsertAttribute( "Status", "Local Connection Failed: ${e.message}" )
    }
}

def SetThermostatFollowSchedule(serialNumber) {
    log.info("Received request to resume schedule for thermostat ${serialNumber}")

    if (!CanProceed()) {
        return
    }

    try {

        LoginIfSessionExpired()

        body = [
            RegulationMode: 1,
            VacationEnabled: false
        ]

        httpPostJson([ uri: "${baseUri}/api/thermostat?sessionId=${GetCurrentSessionId()}&serialnumber=${serialNumber}", body: body ]) {
            resp -> ProcessUpdateThermostatResponse(resp)
        }
    }
    catch (IOException e)
    {
        log.error( "Error connecting to API for status. ${e}" )
        UpsertAttribute( "Status", "Local Connection Failed: ${e.message}" )
    }
}

// Gets the current session id if there is one, null if none or expired
def GetCurrentSessionId() {

    session = state.SessionId

    if (!session) {
        if (DebugLogsEnabled()) log.trace("No current session avaiable")

        return null
    }

    if (OffsetDateTime.now() > OffsetDateTime.parse(session.issueTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME).plusHours(1)) {
        if (DebugLogsEnabled()) log.trace("Session is expired")

        return null
    }

    return decrypt(session.sessionId)
}

def ProcessUpdateThermostatResponse(response) {
    switch( response.getStatus() ){
        case 200:
            if (DebugLogsEnabled()) log.debug( "Succesfully updated thermostat: ${response.getData()}." )
        
            refresh()

            break
        default:
            log.error( "Error connecting to API for updating thermostat. ${ response.getStatus() }" )
            break
    }
}

def ProcessLoginResponse(response) {
    switch( response.getStatus() ){
        case 200:
            ProcessLoginSuccessResponse(response)

            break
        default:
            log.error( "Error connecting to API for login. ${ response.getStatus() }" )
            UpsertAttribute( "Status", "Login Failed" )
            break
    }
}

def ProcessLoginSuccessResponse(response) {

    if (DebugLogsEnabled()) {
        log.debug("Login raw data = \"${response.getData()}\"")
    }

    if (response.data.ErrorCode != 0) {
        log.error( "Login failed with code ${response.data.ErrorCode}" )
        UpsertAttribute( "Status", "Login Failed with code ${response.data.ErrorCode}" )

        return
    }

    state.SessionId = [
        sessionId: encrypt(response.data.SessionId),
        issueTime: OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    ]

    if (DebugLogsEnabled()) {
        log.debug("Succesfully logged in to Schluter API.")
    }
}

def ProcessGetThermostatsResponse(response) {
    switch( response.getStatus() ){
        case 200:

            ProcessGetThermostatsSuccessResponse(response.data)

            if (DebugLogsEnabled()) log.trace( "Succesfully queried thermostats state." )
            UpsertAttribute( "Status", "Query thermostats succeeded" )
            break
        default:
            log.error( "Error connecting to API for getting thermostats. ${response.getStatus()}" )
            UpsertAttribute( "Status", "Query thermostats failed" )
            break
    }
}

def ProcessGetThermostatsSuccessResponse(responseData) {
    
    def thermostats = [:]

    for (group in responseData.Groups) {
        for (thermostat in group.Thermostats) {
            thermostats[thermostat.SerialNumber] = thermostat

            ProcessThermostatUpdate(thermostat)
        }
    }

    getChildDevices().each{
        if (!thermostats[it.deviceNetworkId]) {
            log.info("Removing child thermostat ${it.deviceNetworkId}, ${it.name}.")
            deleteChildDevice( it.deviceNetworkId )
        }
    }
}

def ProcessThermostatUpdate(thermostat) {

    if (!getChildDevice(thermostat.SerialNumber)) {
        addChildDevice("SchluterDitraHeatChild", thermostat.SerialNumber, [
            isComponent: true,
            name: "${thermostat.Room} Thermostat" 
        ])
    }

    getChildDevice(thermostat.SerialNumber).ProcessUpdate(thermostat)
}

// Update an attribute if it has changed
def UpsertAttribute( name, value, unit = null ) {
    
    if (device.currentValue(name) != value) {
        if (unit != null) {
            log.info("Event: ${name} = ${value}${unit}")
            sendEvent(name: name, value: value, unit: unit)
        } else {
            log.info("Event: ${name} = ${value}")
            sendEvent(name: name, value: value)
        }
    }
}

def DebugLogsEnabled() {
     return DebugLogs
}
