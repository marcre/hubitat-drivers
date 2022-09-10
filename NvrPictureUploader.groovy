/*
* NvrPictureUploader
*
* Description:
* This Hubitat driver is designedto capture still images from a NVR and periodically upload them to an Azure storage account.  Those still images can then be used in a dashboard
* that is accessible both locally and remote.
*
* Preferences:
* NvrAddress = REQUIRED. The hostname (or IP address of the network video recorder)
* Username - REQUIRED. Username for account on the NVR
* Password = REQUIRED. Login for the above above
* CameraCount = OPTIONAL - Default 1.  Number of cameras to request still images from.
* AzureStorageAccount - REQUIRED.  Name of the azure storage account to upload pictures to (note.  Assumes public cloud, no national cloud support)
* AzureStorageSasToken - Required.  SAS token enabling upload to the storage account above.
* RefreshRate = OPTIONAL - DEFAULT = 5 minutes. The rate at which images will be captured and uploaded
* LogType = OPTIONAL - DEFAULT = Info. Only basic information will be stored to the log
* ShowAllPreferences = OPTIONAL - DEFAULT = true. Whether the other preferences are to be displayed or not
*
* Features List:
* Uploads images from a HikVision NVR to an Azure Storage account
* 
* Licensing:
* Copyright 2022 Marc Reyhner
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

    Digest auth support based on https://github.com/dlaporte/Hubitat/blob/main/ASH26/device/Amcrest-ASH26.groovy from David LaPorte
    I used the Tesla PowerWall driver fron snell as a sample and skeleton for creating a HTTP based driver
        https://community.hubitat.com/t/project-driver-for-connecting-to-your-tesla-devices/41375

*/

metadata{
    definition ( name: "NvrPictureUploader", namespace: "marcre", author: "Marc Reyhner", importUrl: "tbd" ) {
        // Attempting to indicate what capabilities the device should be capable of
        capability "Refresh"

    }
    preferences{
        section{
            if( ShowAllPreferences || ShowAllPreferences == null ){ // Show the preferences options
                // String to retain the device's IP or hostname
                input( type: "string", name: "NvrAddress", title: "<font color='FF0000'><b>Network Video Recorder's IP/Hostname</b></font>", required: true )
                // String to retain the account name on the video recorder
                input( type: "string", name: "Username", title: "<font color='FF0000'><b>Username</b></font>", required: true )
                // String to retain the account password on the NVR
                input( type: "password", name: "Password", title: "<font color='FF0000'><b>Password</b></font>", required: true )
                // Number of cameras to query
                input( type: "number", name: "CameraCount", title: "<font color='FF0000'><b>Camera Count</b></font>", defaultValue: 1 )
                // Azure storage account to upload images to
                input( type: "string", name: "AzureStorageAccount", title: "<font color='FF0000'><b>Azure Storage Account</b></font>", required: true )
                // SAS token for content upload
                input( type: "password", name: "AzureStorageSasToken", title: "<font color='FF0000'><b>Azure Storage SAS Token</b></font>", required: true )
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

// updated is called whenever device parameters are saved
// Sets the current version of the driver, basic settings, and schedules
def updated(){
    // Set basic info logging if for some reason the preference is null
    if( LogType == null ){
        LogType = "Info"
    }

    // Check if the refresh rate is not set for some reason and putting it at the default
    if( RefreshRate == null ){
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
def refresh(){
    if( NvrAddress != null ){
        for (int i=1; i<=CameraCount; i++)
        {
           CaptureAndUploadImage(i)
        }
        
    } else {
        ProcessEvent( "Status", "Unsuccessful: Lacking device IP address/hostname" )
        Logging( "Cannot query images without hostname or IP address", 5 )
    }
}

def CaptureAndUploadImage( cameraId )
{
        Params = [ uri: "http://${ NvrAddress }/ISAPI/Streaming/channels/${cameraId}01/picture", contentType: "image/jpeg", ignoreSSLIssues: true ] // Aggregated info
        Data = [ uri: Params.uri, finalHandler: "HandleCameraResponse", cameraId: cameraId ]

        // Invoke a request sending to the auth handler first
        asynchttpGet( "HandleAuthentictionResponse", Params, Data )
}

// NVR uses digest so first response will have the challenge we need to respond to.
def HandleAuthentictionResponse( resp, data){
    switch( resp.status ){
        case 200:
            // No auth just call the final handler
            data.finalHandler( resp, data )

            break
        case 401:
            authenticateHeader = resp.getHeaders()['WWW-Authenticate'].toString()
            digestMap = stringToMap(authenticateHeader.replaceAll("Digest ", "").replaceAll("=", ":").replaceAll("\"", "").replaceAll("/", ""))
            authHeaders = [ "Authorization": calcDigestAuth(data.uri, digestMap) ]
            
            // In theory should make resolution configurable
            query = [ "videoResolutionWidth": 4096, "videoResolutionHeight" : 2160 ]

            Params = [ uri: data.uri, contentType: "image/jpeg", ignoreSSLIssues: true, query: query, headers: authHeaders ] // Aggregated info

            asynchttpGet( data.finalHandler, Params, data )
            break
        default:
            Logging( "Error authenticating to NVR for camera ${data.cameraId}. ${ resp.status }", 5 )
            sendEvent( name: "Status", value: "Error authenticating to NVR for camera ${data.cameraId}. ${ resp.status }" )
            break
    }
}

def HandleCameraResponse( resp, data ){
    switch( resp.status ){
        case 200:
            UploadCameraImage(resp.data, data.cameraId)

            break
        default:
            Logging( "Error getting image from NVR for camera ${data.cameraId}. ${ resp.status }", 5 )
            sendEvent( name: "Status", value: "Error getting image from NVR for camera ${data.cameraId}. ${ resp.status }" )
            break
    }
}

def UploadCameraImage(image, cameraId) {

    // Headers for upload to Azure. 
    Headers = [
     "x-ms-blob-type": "BlockBlob",
     "x-ms-blob-content-type": "image/jpeg",
     "cache-control": "private, max-age=60"
    ]
    
    Params = [ 
        uri: "https://${ AzureStorageAccount }.blob.core.windows.net/cameras/camera${cameraId}.jpg",
        queryString: AzureStorageSasToken,
        body: image.decodeBase64(),
        contentType: "application/octet-stream",
        ignoreSSLIssues: false,
        headers: Headers ] // Aggregated info
    
    Logging("Uploading image to ${Params.uri}", 3)

    // Invoke a request sending to the auth handler first
    httpPut( Params ) {
        resp -> Logging( "Uploaded Image ${resp.status}", 3 )
    }
}

def CameraUploadResponse( resp, data ){
    switch( resp.status ){
        case 200:
        case 201:
            Logging( "Uploaded Image", 3 )

            break
        default:
            Logging( "Error uploading image to azure. ${ resp.status } ${ resp.getErrorData()}", 5 )
            sendEvent( name: "Status", value: "Error uploading image to azure. ${ resp.status } ${ resp.getErrorData()}" )
            break
    }
}

private String calcDigestAuth(uri, headers) {
  algorithm = headers.algorithm

  def HA1 = new String(Username + ":" + headers.realm.trim() + ":" + Password)
  def HA1_hash = hash(algorithm, HA1)

  def HA2 = new String("GET:" + uri)
  def HA2_hash = hash(algorithm, HA2)

  // increase nc every request by one
  if (!state.nc) {
    state.nc = 1
  } else {
    state.nc = state.nc + 1
  }

  def cnonce = java.util.UUID.randomUUID().toString().replaceAll('-', '').substring(0, 8)
  def response = new String(HA1_hash + ":" + headers.nonce + ":" + state.nc + ":" + cnonce + ":" + "auth:" + HA2_hash)
  def response_enc = new String(hash(algorithm, response))

  def eol = " "

  return new String('Digest username="' + Username + '",' + eol +
    'realm="' + headers.realm + '",' + eol +
    'qop="' + headers.qop + '",' + eol +
    'algorithm="' + algorithm + '",' + eol +
    'uri="' + uri + '",' + eol +
    'nonce="' + headers.nonce + '",' + eol +
    'cnonce="' + cnonce + '",' + eol +
    'opaque="' + headers.opaque + '",' + eol +
    'nc=' + state.nc + ',' + eol +
    'response="' + response_enc + '"')
}

def hash(algorithm, text) {
  return new String(java.security.MessageDigest.getInstance(algorithm).digest(text.bytes).collect {
    String.format("%02x", it)
  }.join(''))
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