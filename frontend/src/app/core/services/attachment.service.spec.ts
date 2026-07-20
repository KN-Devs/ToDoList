import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../config/api.config';
import { Attachment } from '../models/attachment.model';
import { AttachmentService } from './attachment.service';

const TASK_ID = 5;

const ATTACHMENT: Attachment = {
  id: 1,
  filename: 'plan.pdf',
  contentType: 'application/pdf',
  fileSize: 2048,
  uploadedByEmail: 'marie@example.com',
  createdAt: '2026-01-01T10:00:00Z',
};

describe('AttachmentService', () => {
  let service: AttachmentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AttachmentService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(AttachmentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getForTask() fetches the attachment metadata of a task', () => {
    let result: Attachment[] | undefined;
    service.getForTask(TASK_ID).subscribe((attachments) => (result = attachments));

    const req = httpMock.expectOne(`${API_BASE_URL}/tasks/${TASK_ID}/attachments`);
    expect(req.request.method).toBe('GET');
    req.flush([ATTACHMENT]);

    expect(result).toEqual([ATTACHMENT]);
  });

  it('upload() posts the file as multipart form data', () => {
    const file = new File(['contenu'], 'plan.pdf', { type: 'application/pdf' });
    let result: Attachment | undefined;
    service.upload(TASK_ID, file).subscribe((attachment) => (result = attachment));

    const req = httpMock.expectOne(`${API_BASE_URL}/tasks/${TASK_ID}/attachments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush(ATTACHMENT);

    expect(result).toEqual(ATTACHMENT);
  });

  it('download() requests the file as a blob', () => {
    const blob = new Blob(['contenu']);
    let result: Blob | undefined;
    service.download(TASK_ID, ATTACHMENT.id).subscribe((value) => (result = value));

    const req = httpMock.expectOne(`${API_BASE_URL}/tasks/${TASK_ID}/attachments/${ATTACHMENT.id}/download`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(blob);

    expect(result).toEqual(blob);
  });

  it('delete() removes the attachment', () => {
    let completed = false;
    service.delete(TASK_ID, ATTACHMENT.id).subscribe(() => (completed = true));

    const req = httpMock.expectOne(`${API_BASE_URL}/tasks/${TASK_ID}/attachments/${ATTACHMENT.id}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(completed).toBe(true);
  });
});
