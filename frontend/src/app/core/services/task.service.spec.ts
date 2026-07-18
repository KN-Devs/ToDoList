import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../config/api.config';
import { Task, TaskRequest } from '../models/task.model';
import { TaskService } from './task.service';

const TASK: Task = {
  id: 1,
  nom: 'Préparer la démo',
  description: 'Vérifier le rendu visuel',
  status: 'TODO',
  email: 'marie@example.com',
};

describe('TaskService', () => {
  let service: TaskService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TaskService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(TaskService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getAll() fetches the task list', () => {
    let result: Task[] | undefined;
    service.getAll().subscribe((tasks) => (result = tasks));

    const req = httpMock.expectOne(`${API_BASE_URL}/tasks`);
    expect(req.request.method).toBe('GET');
    req.flush([TASK]);

    expect(result).toEqual([TASK]);
  });

  it('create() posts the new task', () => {
    const request: TaskRequest = { nom: TASK.nom, description: TASK.description, status: 'TODO' };
    let result: Task | undefined;
    service.create(request).subscribe((task) => (result = task));

    const req = httpMock.expectOne(`${API_BASE_URL}/tasks`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(TASK);

    expect(result).toEqual(TASK);
  });

  it('update() puts the modified task', () => {
    const request: TaskRequest = { nom: 'Modifié', description: TASK.description, status: 'DONE' };
    const updated: Task = { ...TASK, nom: 'Modifié', status: 'DONE' };
    let result: Task | undefined;
    service.update(TASK.id, request).subscribe((task) => (result = task));

    const req = httpMock.expectOne(`${API_BASE_URL}/tasks/${TASK.id}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(updated);

    expect(result).toEqual(updated);
  });

  it('delete() removes the task', () => {
    let completed = false;
    service.delete(TASK.id).subscribe(() => (completed = true));

    const req = httpMock.expectOne(`${API_BASE_URL}/tasks/${TASK.id}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(completed).toBe(true);
  });
});
