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
* LogType = OPTIONAL - DEFAULT = Info. Only basic information will be stored to the log
* ShowAllPreferences = OPTIONAL - DEFAULT = true. Whether the other preferences are to be displayed or not
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

metadata{
    definition ( name: "SchluterDitraHeat", namespace: "marcre", author: "Marc Reyhner", importUrl: "tbd" ) {
        // Attempting to indicate what capabilities the device should be capable of
        capability "Refresh"

        // Keep track of connection status
        attribute "Status", "string"
    }
    preferences{
        section{
            if( ShowAllPreferences || ShowAllPreferences == null ){ // Show the preferences options
                // String to retain the device's IP or hostname
                input( type: "string", name: "EmailAddress", title: "<font color='FF0000'><b>Schulter account e-mail address</b></font>", required: true )
                // String to retain the customer's login password
                input( type: "password", name: "Password", title: "<font color='FF0000'><b>Schulter account password</b></font>", required: true )
                // Enum to allow selecting the refresh rate that the device will be checked
                input( type: "enum", name: "RefreshRate", title: "<b>Refresh Rate</b>", required: false, multiple: false, options: [ "5 seconds", "10 seconds", "15 seconds", "30 seconds", "1 minute", "5 minutes", "10 minutes", "15 minutes", "30 minutes", "1 hour", "3 hours", "Manual" ], defaultValue: "5 minutes" )
                // Enum to set the level of logging that will be used
                input( type: "enum", name: "LogType", title: "<b>Enable Logging?</b>", required: true, multiple: false, options: [ "None", "Info", "Debug", "Trace" ], defaultValue: "Info" )
                // Bool to set whether the other preferences should be displayed or not
                input( type: "bool", name: "ShowAllPreferences", title: "<b>Show All Preferences?</b>", defaultValue: true )
            } else { // Preferences should be hidden so only show the preference to show them or not
                input( type: "bool", name: "ShowAllPreferences", title: "<b>Show All Preferences?</b>", defaultValue: true )
            }
        }
    }
}

// uninstalling device so make sure to clean up children
void uninstalled() {
	// Delete all children
	getChildDevices().each{
		deleteChildDevice( it.deviceNetworkId )
	}
	Logging( "Uninstalled", 2 )
}

// updated is called whenever device parameters are saved
// Sets the current version of the driver, basic settings, and schedules
def updated() {

    // On update clear out any old session id in case login information changed
    state.remove("SessionId")

    // Set basic info logging if for some reason the preference is null
    if( LogType == null ) {
        LogType = "Info"
    }

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
    Logging( "Refresh rate: ${ RefreshRate }", 4 )

    Logging( "Updated", 2 )
}

// refresh runs the device polling
def refresh() {
    
    if( EmailAddress == null || Password == null){
        UpsertAttribute( "Status", "Unsuccessful: Lacking account email or password" )
        Logging( "Cannot update thermostats without username or password", 5 )

        return
    }

    baseUri = "https://ditra-heat-e-wifi.schluter.com/"

    try {

        LoginIfSessionExpired(baseUri)

        httpGet([ uri: "${baseUri}/api/thermostats?sessionId=${GetCurrentSessionId()}" ]) {
            resp -> ProcessGetThermostatsResponse(resp)
        }
    }
    catch (IOException e)
    {
        Logging( "Error connecting to API for status. ${e}", 5 )
        UpsertAttribute( "Status", "Local Connection Failed: ${e.message}" ) 
    }
}

def LoginIfSessionExpired(baseUri) {

    if (GetCurrentSessionId()) {
        Logging("Valid session, no need to request new", 3)

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

// Gets the current session id if there is one, null if none or expired
def GetCurrentSessionId() {

    session = state.SessionId

    if (!session) {
        Logging("No current session avaiable", 3)

        return null
    }

    if (OffsetDateTime.now() > OffsetDateTime.parse(session.issueTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME).plusHours(1)) {
        Logging("Session is expired", 3)

        return null
    }

    return decrypt(session.sessionId)
}

def ProcessLoginResponse(response) {
    switch( response.getStatus() ){
        case 200:
            Logging( "Login raw data = \"${ response.getData() }\"", 4 )

            if (response.data.ErrorCode != 0) {
                Logging( "Login failed with code ${response.data.ErrorCode}", 5 )
                UpsertAttribute( "Status", "Login Failed with code ${response.data.ErrorCode}" )

                return
            }

            state.SessionId = [
                sessionId: encrypt(response.data.SessionId),
                issueTime: OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            ]

            Logging( "Succesfully logged in to Schluter API.", 4 )
            UpsertAttribute( "Status", "Login Succeeded" )
            break
        default:
            Logging( "Error connecting to API for login. ${ response.getStatus() }", 5 )
            UpsertAttribute( "Status", "Login Failed" )
            break
    }
}

def ProcessGetThermostatsResponse(response) {
    switch( response.getStatus() ){
        case 200:

            def thermostats = [:]

            for (group in response.data.Groups) {
                for (thermostat in group.Thermostats) {
                    thermostats[thermostat.SerialNumber] = thermostat
                    
                    ProcessThermostatUpdate(thermostat)
                }
            }

            getChildDevices().each{
                if (!thermostats[it.deviceNetworkId]) {
                    Logging("Removing child thermostat ${it.deviceNetworkId}, ${it.name}.", 2)
                    deleteChildDevice( it.deviceNetworkId )
                }
            }

            Logging( "Succesfully queried thermostats state.", 4 )
            UpsertAttribute( "Status", "Query thermostats succeeded" )
            break
        default:
            Logging( "Error connecting to API for getting thermostats. ${ response.getStatus() }", 5 )
            UpsertAttribute( "Status", "Query thermostats failed" )
            break
    }
}

def ProcessThermostatUpdate(thermostat) {

    if (!getChildDevice(thermostat.SerialNumber)) {
        addChildDevice("SchluterDitraHeatChild", thermostat.SerialNumber, [
            isComponent: true,
            name: thermostat.Room
        ])
    }

    getChildDevice(thermostat.SerialNumber).ProcessUpdate(thermostat)
}

// Update an attribute if it has changed
def UpsertAttribute( Variable, Value, Unit = null ){
    if( device.currentValue(Variable) != Value ){

        if( Unit != null ){
            Logging( "Event: ${ Variable } = ${ Value }${ Unit }", 2 )
            sendEvent( name: "${ Variable }", value: Value, unit: Unit )
        } else {
            Logging( "Event: ${ Variable } = ${ Value }", 1 )
            sendEvent( name: "${ Variable }", value: Value )
        }
    }
}

// Handles whether logging is enabled and thus what to put there.
def Logging( LogMessage, LogLevel ){
    // Add all messages as info logging
    if( ( LogLevel == 2 ) && ( LogType != "None" ) ){
        log.info( "${ device.displayName } - ${ LogMessage }" )
    } else if( ( LogLevel == 3 ) && ( ( LogType == "Debug" ) || ( LogType == "Trace" ) ) ){
        log.debug( "${ device.displayName } - ${ LogMessage }" )
    } else if( ( LogLevel == 4 ) && ( LogType == "Trace" ) ){
        log.trace( "${ device.displayName } - ${ LogMessage }" )
    } else if( LogLevel == 5 ){
        log.error( "${ device.displayName } - ${ LogMessage }" )
    }
}