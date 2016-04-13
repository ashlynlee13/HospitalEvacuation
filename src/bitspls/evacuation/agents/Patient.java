package bitspls.evacuation.agents;

import java.util.ArrayList;
import java.util.List;

import bitspls.evacuation.Door;
import bitspls.evacuation.agents.Doctor;
import bitspls.evacuation.agents.GasParticle;
import bitspls.evacuation.agents.Human;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.query.space.grid.GridCell;
import repast.simphony.query.space.grid.GridCellNgh;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridDimensions;
import repast.simphony.space.grid.GridPoint;
import repast.simphony.util.SimUtilities;

public class Patient extends Human {
	private static final int SPEED = 2;

	private PatientMode movementMode;
	private Doctor doctorToFollow;
	private Door door;
	private boolean exited;
	private double panic;
	private double worstCase;
	
	public Patient(ContinuousSpace<Object> space, Grid<Object> grid) {
		this.setSpace(space);
		this.setGrid(grid);
		this.setDead(false);
		this.setRadiusOfKnowledge(10);
		this.setSpeed(SPEED);
		this.setPanic(0.5);
		this.setWorstCase(calculateWorstCaseScenario());
		this.movementMode = PatientMode.AVOID_GAS;
		this.doctorToFollow = null;
		this.door = null;
		this.exited = false;
	}
	
	@ScheduledMethod(start = 1, interval = 1)
	public void run() {
		if (!isDead() && !this.exited) {
			this.setPanic(this.calculateNewPanicLevel());

			if (this.doctorToFollow == null || this.door != null) {
				Doctor targetDoctor = findDoctorWithMaxCharisma();
				if(targetDoctor != null && shouldFollowDoctorAgent(targetDoctor)) {
					this.doctorToFollow = targetDoctor;
					this.doctorToFollow.startFollowing();
					this.movementMode = PatientMode.FOLLOW_DOCTOR;
				}
			}
			
			if (this.movementMode != PatientMode.APPROACH_DOOR) {
				Door closestDoor = findClosestDoor();
				if (closestDoor != null) {
					this.door = closestDoor;
					this.movementMode = PatientMode.APPROACH_DOOR;
				}
			}
			
			determineNextAction(findNextPointToMoveTo());
		}
	}
	
	private void determineNextAction(GridPoint pointToMoveTo) {
		if (pointToMoveTo != null) {
			moveTowards(pointToMoveTo);
		} else {
			this.kill();
		}
	}
	
	private GridPoint findNextPointToMoveTo() {
		GridPoint currentLocation = this.getGrid().getLocation(this);
		GridPoint leastGasPoint = findLeastGasPoint(currentLocation);
		GridPoint pointToMoveTo = null;
		
		if (this.movementMode == PatientMode.APPROACH_DOOR) {
			pointToMoveTo = this.getGrid().getLocation(this.door);
		} else if (this.movementMode == PatientMode.FOLLOW_DOCTOR && !this.doctorToFollow.isDead()) {
			pointToMoveTo = this.getGrid().getLocation(this.doctorToFollow);
		} else if (leastGasPoint != null) {
			pointToMoveTo = leastGasPoint;
		}
		
		return pointToMoveTo;
	}
	
	private GridPoint findLeastGasPoint(GridPoint pt) {
		GridCellNgh<GasParticle> nghCreator = new GridCellNgh<GasParticle>(this.getGrid(), pt, GasParticle.class, this.getRadiusOfKnowledge(), this.getRadiusOfKnowledge());
		List<GridCell<GasParticle>> gridCells = nghCreator.getNeighborhood(true);
		SimUtilities.shuffle(gridCells, RandomHelper.getUniform());
		
		GridPoint pointWithLeastGas = null;
		for (GridCell<GasParticle> cell : gridCells) {
			if (cell.size() == 0) {
				pointWithLeastGas = cell.getPoint();
			}
		}
		return pointWithLeastGas;
	}
	
	protected void moveTowards(GridPoint pt) {
		super.moveTowards(pt);
		
		if (this.door != null) {
			GridPoint currentPt = this.getGrid().getLocation(this);
			GridCellNgh<Door> doorNghCreator = new GridCellNgh<Door>(this.getGrid(), currentPt, Door.class, 0, 0);
			List<GridCell<Door>> doorGridCells = doorNghCreator.getNeighborhood(true);
			GridPoint doorPt = this.getGrid().getLocation(this.door);
			if (doorGridCells.contains(doorPt)) {
				this.exited = true;
				this.doctorToFollow.stopFollowing();
				this.doctorToFollow = null;
				this.door = null;
				System.out.println("Patient exited");
			}
		}
	}

	public boolean shouldFollowDoctorAgent(Doctor doctor) {
		double probabilityOfFollowingDoctor = 0.4*doctor.getCharisma() + 0.6*(1 - getPanic());
		return randomFollowGenerator(probabilityOfFollowingDoctor);
	}
	
	public boolean randomFollowGenerator(double probabilityTrue)
	{
	    return Math.random() >= 1.0 - probabilityTrue;
	}
	
	public double calculateNewPanicLevel() {
		return (0.4 * getPanic()) + /*(0.3 * calculatePatientsPanicFactor())*/ + (0.3 * calculateGasParticleFactor() );
	}
	
	private Door findClosestDoor() {
		GridPoint currentLocation = this.getGrid().getLocation(this);
		GridCellNgh<Door> doorNghCreator = new GridCellNgh<Door>(this.getGrid(), currentLocation, Door.class, this.getRadiusOfKnowledge(), this.getRadiusOfKnowledge());
		List<GridCell<Door>> doorGridCells = doorNghCreator.getNeighborhood(true);
		
		double minimumDistance = Double.POSITIVE_INFINITY;
		Door closestDoor = null;
		
		for (GridCell<Door> cell : doorGridCells) {
			if (cell.size() > 0) {
				for (Door door : cell.items()) {
					double distance = getGrid().getDistance(currentLocation, getGrid().getLocation(door));	
					if (distance < minimumDistance) {
						closestDoor = door;
					}
				}
			}
		}
		
		return closestDoor;
	}
	
	private Doctor findDoctorWithMaxCharisma() {
		GridPoint currentLocation = this.getGrid().getLocation(this);
		GridCellNgh<Doctor> doctorNghCreator = new GridCellNgh<Doctor>(this.getGrid(), currentLocation, Doctor.class, this.getRadiusOfKnowledge(), this.getRadiusOfKnowledge());
		List<GridCell<Doctor>> doctorGridCells = doctorNghCreator.getNeighborhood(true);
		SimUtilities.shuffle(doctorGridCells, RandomHelper.getUniform());
		
		double maxCharisma = 0.0;
		Doctor maxDoctor = null;
		for (GridCell<Doctor> cell : doctorGridCells) {
			if (cell.size() > 0) {
				for (Doctor doc : cell.items()) {
					if(doc.getCharisma() > maxCharisma) {
						maxDoctor = doc;
						maxCharisma = doc.getCharisma();
					}
				}
			}
		}
		
		return maxDoctor;
	}
	
	private List<GridCell<GasParticle>> findGasAgentsInRadiusOfKnowledge () {
		int radiusOfKnowledge = getRadiusOfKnowledge();
		GridPoint location = getGrid().getLocation(this);

		GridCellNgh<GasParticle> nghCreator = new GridCellNgh<GasParticle>(getGrid(), location, GasParticle.class, radiusOfKnowledge, radiusOfKnowledge);
		List<GridCell<GasParticle>> gridCells = nghCreator.getNeighborhood(true);
		
		List<GridCell<GasParticle>> gasAgentLocation = new ArrayList<GridCell<GasParticle>>();
		
		for (GridCell<GasParticle> cell : gridCells) {
			if(cell.size() > 0) {
				gasAgentLocation.add(cell);
			}
		}

		return gasAgentLocation;
	}
	
	private List<Patient> findPatientAgentsInRadiusOfKnowledge() {
		int radiusOfKnowledge = getRadiusOfKnowledge();
		GridPoint location = getGrid().getLocation(this);
		
		GridCellNgh<Patient> nghCreator = new GridCellNgh<Patient>(getGrid(), location, Patient.class, radiusOfKnowledge, radiusOfKnowledge);
		List<GridCell<Patient>> gridCells = nghCreator.getNeighborhood(true);

		List<Patient> patientsInRadius = new ArrayList<Patient>();
		
		for (GridCell<Patient> cell : gridCells) {
			for (Object obj: getGrid().getObjectsAt(cell.getPoint().getX(), cell.getPoint().getY())) {
				if (obj instanceof Patient) {
					patientsInRadius.add((Patient) obj);
				}
			}
		}
		return patientsInRadius;
	}
	
	private double calculatePatientsPanicFactor() {
		List<Patient> patients = findPatientAgentsInRadiusOfKnowledge();
		
		double worstCase = getWorstCase();
		if (worstCase != 0) {
			return calculateSurroundingPatientsPanicFactor(patients)/worstCase;
		}
		else {
			return 0;
		}
	}
	
	private double calculateSurroundingPatientsPanicFactor(List<Patient> patients) {
		GridPoint currentLocation = getGrid().getLocation(this);
		double totalPatientFactor = 0.0;
		
		for(Patient patient: patients) {
			GridPoint pt = getGrid().getLocation(patient);
			double distance = getGrid().getDistance(currentLocation, pt);
			//System.out.println("distance: " + distance);
			if (distance != 0) {
				//System.out.println("panic: " + patient.getPanic());
				totalPatientFactor += patient.getPanic()/distance;
			}
			//System.out.println("totalPatientFactor: " + totalPatientFactor);
		}
		
		return totalPatientFactor;
	}
	
	private double calculateWorstCaseScenario() {
		GridDimensions dimensions = getGrid().getDimensions();
		GridPoint center = new GridPoint(dimensions.getWidth()/2, dimensions.getHeight()/2);
		
		int radiusOfKnowledge = getRadiusOfKnowledge();
		double total = 0.0;
		
		GridCellNgh<Patient> nghCreator = new GridCellNgh<Patient>(getGrid(), center, Patient.class, radiusOfKnowledge, radiusOfKnowledge);
		List<GridCell<Patient>> gridCells = nghCreator.getNeighborhood(false);

		for (GridCell<Patient> cell : gridCells) {
			GridPoint currentPt = cell.getPoint();
			double distance = getGrid().getDistance(center, currentPt);
			if (distance != 0) {
				total += 1/distance;
			}
		}
		
		return total;
	}
	
	private double calculateGasParticleFactor() {
		List<GridCell<GasParticle>> surroundingGas = findGasAgentsInRadiusOfKnowledge();
		return calculateSurroundingGasParticleFactor(surroundingGas)/worstCase;
	}
	
	private double calculateSurroundingGasParticleFactor(List<GridCell<GasParticle>> gasAgents) {
		GridPoint location = getGrid().getLocation(this);
		double totalGasFactor = 0.0;
		
		for(GridCell<GasParticle> gas: gasAgents) {
			GridPoint pt = gas.getPoint();
			double distance = getGrid().getDistance(location, pt);
			totalGasFactor += 1/distance;
		}
		
		return totalGasFactor;
	}
	
	public double getPanic() {
		return this.panic;
	}
	
	public void setPanic(double panic) {
		this.panic = panic;
	}
	
	public double getWorstCase() {
		return this.worstCase;
	}
	
	public void setWorstCase(double worstCase) {
		this.worstCase = worstCase;
	}
	
	enum PatientMode {
		AVOID_GAS,
		FOLLOW_DOCTOR,
		APPROACH_DOOR
	}
}
