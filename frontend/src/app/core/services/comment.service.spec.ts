import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../config/api.config';
import { Comment } from '../models/comment.model';
import { CommentService } from './comment.service';

const TASK_ID = 5;

const COMMENT: Comment = {
  id: 1,
  content: 'Un commentaire',
  authorEmail: 'marie@example.com',
  createdAt: '2026-01-01T10:00:00Z',
};

describe('CommentService', () => {
  let service: CommentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CommentService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(CommentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getForTask() fetches the comments of a task', () => {
    let result: Comment[] | undefined;
    service.getForTask(TASK_ID).subscribe((comments) => (result = comments));

    const req = httpMock.expectOne(`${API_BASE_URL}/tasks/${TASK_ID}/comments`);
    expect(req.request.method).toBe('GET');
    req.flush([COMMENT]);

    expect(result).toEqual([COMMENT]);
  });

  it('create() posts the comment content', () => {
    let result: Comment | undefined;
    service.create(TASK_ID, 'Un commentaire').subscribe((comment) => (result = comment));

    const req = httpMock.expectOne(`${API_BASE_URL}/tasks/${TASK_ID}/comments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ content: 'Un commentaire' });
    req.flush(COMMENT);

    expect(result).toEqual(COMMENT);
  });

  it('delete() removes the comment', () => {
    let completed = false;
    service.delete(TASK_ID, COMMENT.id).subscribe(() => (completed = true));

    const req = httpMock.expectOne(`${API_BASE_URL}/tasks/${TASK_ID}/comments/${COMMENT.id}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(completed).toBe(true);
  });
});
