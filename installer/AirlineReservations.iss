; Airline Reservations - Inno Setup kurulum betiği
; Derleme: önce installer\build.ps1 ile dist\AirlineReservations klasörünü oluştur, sonra bu dosyayı ISCC ile derle.
; Kullanıcı verisi (%APPDATA%\MrcDprm\AirlineReservations) program klasöründe değildir; kaldırınca silinmez.

#define AppName "Airline Reservations"
#define AppVersion "1.0.0"
#define AppPublisher "Miraç Deprem"
#define AppURL "https://github.com/MrcDprm/airline-reservation-system"
#define AppExeName "AirlineReservations.exe"

[Setup]
AppId={{A72FE6B2-F132-4D61-B34A-55E5CAEBE06D}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}/issues
DefaultDirName={autopf}\AirlineReservations
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir=Output
OutputBaseFilename=AirlineReservations-{#AppVersion}-Setup
SetupIconFile=icon.ico
LicenseFile=..\LICENSE
UninstallDisplayIcon={app}\{#AppExeName}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "turkish"; MessagesFile: "compiler:Languages\Turkish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "..\dist\AirlineReservations\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(AppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
