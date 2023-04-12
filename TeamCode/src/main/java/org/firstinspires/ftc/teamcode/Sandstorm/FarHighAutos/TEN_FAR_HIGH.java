package org.firstinspires.ftc.teamcode.Sandstorm.FarHighAutos;
import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.acmerobotics.roadrunner.trajectory.Trajectory;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.teamcode.Sandstorm.AutoTest.AprilTagDetectionPipeline;
import org.firstinspires.ftc.teamcode.Sandstorm.Outtake;
import org.firstinspires.ftc.teamcode.Sandstorm.PoseStorage;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;
import org.openftc.apriltag.AprilTagDetection;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;
import java.util.ArrayList;

@Disabled
@Autonomous(name = "1+10 Far-High Auto", group = "Autonomous")
public class TEN_FAR_HIGH extends LinearOpMode {

    // class members
    ElapsedTime GlobalTimer;
    double autoTimer;

    final int IntakeSlideOutTicks = -400;
    final int LiftHighPosition = 800;
    final int LiftMidPosition = 400;
    final int LiftLowPosition = 0;

    final int TurretLeftPosition = -8;
    final int TurretRightposition = 8;

    boolean outakeResetReady;
    boolean outakeOutReady;
    int numCycles;
    int SignalRotation;
    int slowerVelocityConstraint;

    final double outconestackX = 28;
    final double outconestackY = -12;


    // create class instances

    Outtake outtake = new Outtake();
    OpenCvCamera camera;
    AprilTagDetectionPipeline aprilTagDetectionPipeline;

    static final double FEET_PER_METER = 3.28084;

    // Lens intrinsics
    // UNITS ARE PIXELS
    // NOTE: this calibration is for the C920 webcam at 800x448.
    // You will need to do your own calibration for other configurations!
    double fx = 578.272;
    double fy = 578.272;
    double cx = 402.145;
    double cy = 221.506;

    // UNITS ARE METERS
    double tagsize = 0.166;

    AprilTagDetection tagOfInterest = null;


    enum AutoState {
        PRELOAD_DRIVE,
        OUTTAKE_CONE,
        GRAB_OFF_STACK,
        AFTER_GRAB_OFF_STACK,
        TRANSFER_CONE,
        OUTTAKE_CONE_NO_INTAKE_SLIDES,
        RETRACT_SLIDES,
        DRIVE_OTHER_SIDE_AND_TRANSFER,
        PARK,
        IDLE,
    }

    // We define the current state we're on
    // Default to IDLE
    AutoState currentState;

    private void Setup() {
        GlobalTimer = new ElapsedTime(System.nanoTime());
        GlobalTimer.reset();
        outtake.hardwareSetup();
        currentState = AutoState.PRELOAD_DRIVE; // this go here?
        autoTimer = 0;
        numCycles = 0;
        slowerVelocityConstraint = 12;
    }
    // Define our start pose
    Pose2d startPose = new Pose2d(38, -69, Math.toRadians(90));

    @Override
    public void runOpMode() throws InterruptedException {

        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        camera = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class, "WebcamLeft"), cameraMonitorViewId);
        aprilTagDetectionPipeline = new AprilTagDetectionPipeline(tagsize, fx, fy, cx, cy);

        camera.setPipeline(aprilTagDetectionPipeline);
        camera.openCameraDeviceAsync(new OpenCvCamera.AsyncCameraOpenListener()
        {
            @Override
            public void onOpened()
            {
                camera.startStreaming(800,448, OpenCvCameraRotation.UPRIGHT);
            }

            @Override
            public void onError(int errorCode)
            {

            }
        });


        // initialize hardware
        outtake.Outtake_init(hardwareMap);
        SampleMecanumDrive drive = new SampleMecanumDrive(hardwareMap); // road drive class

        // functions runs on start
        Setup();
        // Set inital pose
        drive.setPoseEstimate(startPose);

        // trajectories that aren't changing should all be here

        Trajectory PreloadDrive = drive.trajectoryBuilder(startPose)
                .lineToLinearHeading(new Pose2d(36, -12, Math.toRadians(0)))
                .build();

        Trajectory DriveOutStackAfterPreload = drive.trajectoryBuilder(PreloadDrive.end()) // actual drive out will occur during loop
                .lineToLinearHeading(new Pose2d(outconestackX, outconestackY, Math.toRadians(0)))
                .build();

        Trajectory DriveIntoStack = drive.trajectoryBuilder(DriveOutStackAfterPreload.end()) //
                .lineToLinearHeading(new Pose2d(36, outconestackY, Math.toRadians(0)))
                .build();

        Trajectory DriveIntoStackOtherSide = drive.trajectoryBuilder(DriveOutStackAfterPreload.end()) //
                .lineToLinearHeading(new Pose2d(-36, outconestackY, Math.toRadians(180)))
                .build();

        Trajectory DriveOtherSide = drive.trajectoryBuilder(DriveIntoStack.end())
                .lineToLinearHeading(new Pose2d(-20, outconestackY, Math.toRadians(180)))
                .splineToSplineHeading(new Pose2d(-38, outconestackY, Math.toRadians(0)), Math.toRadians(180))
                .build();

        Trajectory ParkRight = drive.trajectoryBuilder(DriveOtherSide.end())
                .lineTo(new Vector2d(-8,outconestackY))
                .build();

        Trajectory ParkLeft = drive.trajectoryBuilder(DriveOtherSide.end())
                .lineTo(new Vector2d(-64,outconestackY))
                .build();

        Trajectory ParkCentre = drive.trajectoryBuilder(DriveOtherSide.end())
                .lineTo(new Vector2d(-35,outconestackY))
                .build();

        while (!isStarted()) {
            ArrayList<AprilTagDetection> currentDetections = aprilTagDetectionPipeline.getLatestDetections();

            if(currentDetections.size() != 0)
            {
                boolean tagFound = false;

                for(AprilTagDetection tag : currentDetections)
                {
                    if(tag.id == 1 || tag.id == 2 || tag.id == 3)
                    {
                        tagOfInterest = tag;
                        tagFound = true;
                        break;
                    }
                }

                if(tagFound)
                {
                    telemetry.addLine("Tag of interest is in sight!\n\nLocation data:");
                    tagToTelemetry(tagOfInterest);
                }
                else
                {
                    telemetry.addLine("Don't see tag of interest :(");

                    if(tagOfInterest == null)
                    {
                        telemetry.addLine("(The tag has never been seen)");
                    }
                    else
                    {
                        telemetry.addLine("\nBut we HAVE seen the tag before; last seen at:");
                        tagToTelemetry(tagOfInterest);
                    }
                }

            }
            else
            {
                telemetry.addLine("Don't see tag of interest :(");

                if(tagOfInterest == null)
                {
                    telemetry.addLine("(The tag has never been seen)");
                }
                else
                {
                    telemetry.addLine("\nBut we HAVE seen the tag before; last seen at:");
                    tagToTelemetry(tagOfInterest);
                }

            }

            telemetry.update();
            sleep(20);
            outtake.OuttakeClawClose();
        }


        waitForStart();
        if (isStopRequested()) return;

        // open cv changes the state at the start depending on cone rotation
        // open cv vision if statements to change variable and display telemetry here
        if(tagOfInterest == null || tagOfInterest.id == 1) {
            telemetry.addLine("Rotation Left");
            SignalRotation = 1;
        }
        else if(tagOfInterest.id == 2) {
            telemetry.addLine("Rotation Centre");
            SignalRotation = 2;
        }
        else {
            telemetry.addLine("Rotation Right");
            SignalRotation = 3;
        }

        // runs instantly once
        drive.followTrajectoryAsync(PreloadDrive);
        autoTimer = GlobalTimer.milliseconds(); // reset timer not rly needed here
        outtake.IntakeClawClose();

        while (opModeIsActive() && !isStopRequested()) {
            // Read pose
            Pose2d poseEstimate = drive.getPoseEstimate(); // gets the position of the robot

            // Print pose to telemetry
            // telemetry.addData("x", poseEstimate.getX());
            // telemetry.addData("y", poseEstimate.getY());
            telemetry.addData("autostate", currentState);
            telemetry.addData("number of cycles:", numCycles);

            // main switch statement logic
            switch (currentState) {
                case PRELOAD_DRIVE:
                    outtake.IntakeSlideInternalPID(0,1); // might break something
                    outtake.liftTo(0, outtake.liftPos(),1);
                    outtake.turretSpinInternalPID(0,1);
                    outtake.OuttakeSlideReady();
                    outtake.OuttakeClawClose();
                    outtake.OuttakeArmReady();
                    outtake.IntakeClawOpenHard();
                    outtake.IntakeLift5();
                    if (!drive.isBusy()){
                        autoTimer = GlobalTimer.milliseconds(); // reset timer
                        currentState = AutoState.OUTTAKE_CONE;
                        drive.followTrajectoryAsync(DriveOutStackAfterPreload);
                        outtake.OuttakeArmScore();
                        outtake.BraceActive();
                    }
                    break;

                case OUTTAKE_CONE:
                    OuttakeCone(true); // next state is grab off stack
                    if (outtake.liftTargetReached() && !drive.isBusy()){
                        autoTimer = GlobalTimer.milliseconds(); // reset timer not rly needed here
                        currentState = AutoState.GRAB_OFF_STACK;
                        outtake.OuttakeArmScore();
                        outtake.BraceActive();
                        numCycles += 1;
                        drive.followTrajectoryAsync(DriveIntoStackOtherSide);
                    }
                    break;

                case GRAB_OFF_STACK:
                    // this section is for scoring the cone
                    outtake.OuttakeSlideScoreDrop(); // drops down on pole a bit
                    outtake.OuttakeArmDeposit();
                    if (GlobalTimer.milliseconds() - autoTimer > 50){
                        outtake.OuttakeClawOpenHard();
                        if (GlobalTimer.milliseconds() - autoTimer > 75){
                            outtake.BraceReady(); // might need a new position for this
                            if (GlobalTimer.milliseconds() - autoTimer > 200){
                                outtake.liftTo(0, outtake.liftPos(), 1);
                                outtake.turretSpin(0, outtake.turretPos(),1);
                                outtake.OuttakeArmReady();
                            }
                        }
                    }
                    // this section is for scoring the cone
                    if (GlobalTimer.milliseconds() - autoTimer > 400) {
                        if (outtake.intakeClawTouchPressed() || !drive.isBusy()){ // if it drives into the cone basically
                            outtake.IntakeSlideTo(IntakeSlideOutTicks, outtake.IntakeSlidePos(), 0); // stop the intake slides
                            outtake.IntakeClawClose();
                            autoTimer = GlobalTimer.milliseconds(); // reset timer not rly needed here
                            currentState = AutoState.AFTER_GRAB_OFF_STACK;
                            if (numCycles > 5) {
                                Trajectory OutConeStackOtherSide = drive.trajectoryBuilder(poseEstimate)
                                        .lineToLinearHeading(new Pose2d(-outconestackX, outconestackY, Math.toRadians(180)))
                                        .build();
                                drive.followTrajectoryAsync(OutConeStackOtherSide); // move backwards instantly - might not work
                            } else {
                                Trajectory OutConeStack = drive.trajectoryBuilder(poseEstimate)
                                        .lineToLinearHeading(new Pose2d(outconestackX, outconestackY, Math.toRadians(0)))
                                        .build();

                                drive.followTrajectoryAsync(OutConeStack); // move backwards instantly - might not work
                            }
                        } else {
                            outtake.IntakeSlideTo(IntakeSlideOutTicks, outtake.IntakeSlidePos(), 1); // slower
                        }
                    }
                    break;
                case AFTER_GRAB_OFF_STACK:
                    outtake.IntakeClawClose();
                    if (GlobalTimer.milliseconds() - autoTimer > 200){
                        outtake.IntakeLiftTransfer();
                        if (GlobalTimer.milliseconds()-autoTimer > 200){
                            outtake.IntakeArmTransfer();
                            if (GlobalTimer.milliseconds()-autoTimer > 300){
                                outtake.IntakeSlideTo(0,outtake.IntakeSlidePos(),1);
                                // drivebrase.spin inwards and stuff
                                if (outtake.intakeSlideTargetReached()){
                                    if (numCycles == 5){
                                        autoTimer = GlobalTimer.milliseconds(); // reset timer not rly needed here
                                        currentState = AutoState.DRIVE_OTHER_SIDE_AND_TRANSFER;
                                        drive.followTrajectoryAsync(DriveOtherSide);
                                        outtake.OuttakeClawClose();
                                    } else {
                                        autoTimer = GlobalTimer.milliseconds(); // reset timer not rly needed here
                                        currentState = AutoState.TRANSFER_CONE;
                                        outtake.OuttakeClawClose();
                                    }
                                }
                            }
                        }
                    }
                    break;
                case TRANSFER_CONE:
                    outtake.OuttakeClawClose();
                    if (GlobalTimer.milliseconds()-autoTimer > 150){ // time between claw transfers
                        outtake.IntakeClawOpen();
                        if (GlobalTimer.milliseconds()-autoTimer > 230){
                            if (numCycles == 10){
                                outtake.IntakeArmReady();
                                outtake.IntakeLift3();

                                autoTimer = GlobalTimer.milliseconds(); // reset timer
                                currentState = AutoState.OUTTAKE_CONE_NO_INTAKE_SLIDES;
                            } else{
                                outtake.IntakeArmReady();
                                outtake.IntakeLift3();
                                outtake.IntakeSlideTo(IntakeSlideOutTicks - 100, outtake.IntakeSlidePos(), 1);

                                autoTimer = GlobalTimer.milliseconds(); // reset timer
                                currentState = AutoState.OUTTAKE_CONE;
                            }
                        }
                    }
                    break;
                case DRIVE_OTHER_SIDE_AND_TRANSFER:
                    outtake.OuttakeClawClose();
                    if (GlobalTimer.milliseconds()-autoTimer > 150){ // time between claw transfers
                        outtake.IntakeClawOpen();
                        if (!drive.isBusy()){ // if its finished driving
                            outtake.IntakeArmReady();
                            outtake.IntakeLift3();
                            outtake.IntakeSlideTo(IntakeSlideOutTicks, outtake.IntakeSlidePos(), 1);
                            autoTimer = GlobalTimer.milliseconds(); // reset timer
                            currentState = AutoState.OUTTAKE_CONE;
                        }
                    }
                    break;
                case OUTTAKE_CONE_NO_INTAKE_SLIDES:
                    OuttakeCone(false); // in a function so that i don't have eto make 2 changes
                    if (outtake.liftTargetReached() && !drive.isBusy()){
                        autoTimer = GlobalTimer.milliseconds(); // reset timer not rly needed here
                        currentState = AutoState.RETRACT_SLIDES;
                        outtake.OuttakeArmScore();
                        outtake.BraceActive();
                        numCycles += 1;
                    }
                    break;
                case RETRACT_SLIDES:
                    outtake.OuttakeClawOpen();
                    outtake.OuttakeSlideScoreDrop();
                    outtake.BraceReady();
                    if (GlobalTimer.milliseconds() - autoTimer > 150){
                        outtake.liftTo(0, outtake.liftPos(), 1);
                        outtake.BraceReady();
                        outtake.OuttakeArmReady();
                        outtake.turretSpin(0,outtake.turretPos(),1); // spin turret after
                        autoTimer = GlobalTimer.milliseconds(); // reset timer not rly needed here
                        currentState = AutoState.PARK;
                    }
                    break;


                case PARK:
                    if (SignalRotation == 1){
                        drive.followTrajectoryAsync(ParkLeft);
                        currentState = AutoState.IDLE;
                    }
                    else if (SignalRotation == 3){
                        drive.followTrajectoryAsync(ParkRight);
                        currentState = AutoState.IDLE;
                    }
                    else{
                        drive.followTrajectoryAsync(ParkCentre);
                        currentState = AutoState.IDLE; // doesn't have to drive anywhere, already in position hopefully
                    }

                    break;

                case IDLE:
                    telemetry.addLine("WWWWWWWWWWW");
                    PoseStorage.currentPose = drive.getPoseEstimate(); // this stores the pose for teleop
                    break;

            }
            // Updates driving for trajectories
            drive.update();
            telemetry.update();
        }

    }

    public void intakeLiftHeight(){
        if (numCycles == 0){
            outtake.IntakeLift5();
        } else if (numCycles == 1){
            outtake.IntakeLift4();
        } else if (numCycles == 2){
            outtake.IntakeLift3();
        } else if (numCycles == 3){
            outtake.IntakeLift2();
        } else if (numCycles == 4){
            outtake.IntakeLift1();
        } else if (numCycles == 6){
            outtake.IntakeLift5();
        } else if (numCycles == 7){
            outtake.IntakeLift4();
        } else if (numCycles == 8){
            outtake.IntakeLift3();
        } else if (numCycles == 9){
            outtake.IntakeLift2();
        } else if (numCycles == 10){
            outtake.IntakeLift1();
        }
    }

    public void OuttakeCone(boolean Intake){
        if (Intake){ // if parameter is set to false it won't do the intake slides just outtake
            outtake.IntakeSlideTo(IntakeSlideOutTicks, outtake.IntakeSlidePos(), 1); // move to just before the stack
            intakeLiftHeight();
            outtake.IntakeArmReady();
        }

        outtake.liftTo(LiftHighPosition, outtake.liftPos(), 1);
        outtake.turretSpin(TurretRightposition, outtake.turretPos(),1);
        outtake.OuttakeArmScore();
        if (GlobalTimer.milliseconds()-autoTimer > 120){ // this is the right time
            outtake.BraceActive();
        }
    }

    public void ResetOuttake(){
        outtake.liftTo(0, outtake.liftPos(), 1);
        outtake.turretSpin(0, outtake.turretPos(),1);
        outtake.OuttakeArmReady();
        outtake.BraceReady();
        outtake.OuttakeClawOpen();
        outtake.OuttakeSlideReady();
    }

    void tagToTelemetry(AprilTagDetection detection) {
        telemetry.addLine(String.format("\nDetected tag ID=%d", detection.id));
        telemetry.addLine(String.format("Translation X: %.2f feet", detection.pose.x*FEET_PER_METER));
        telemetry.addLine(String.format("Translation Y: %.2f feet", detection.pose.y*FEET_PER_METER));
        telemetry.addLine(String.format("Translation Z: %.2f feet", detection.pose.z*FEET_PER_METER));
        telemetry.addLine(String.format("Rotation Yaw: %.2f degrees", Math.toDegrees(detection.pose.yaw)));
        telemetry.addLine(String.format("Rotation Pitch: %.2f degrees", Math.toDegrees(detection.pose.pitch)));
        telemetry.addLine(String.format("Rotation Roll: %.2f degrees", Math.toDegrees(detection.pose.roll)));
    }
}


